package uz.khakimjanovich.platform.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.node.ObjectNode;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Client;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Credential;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Enabled;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Failure;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Identified;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Role;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.Secret;
import uz.khakimjanovich.platform.keycloak.KeycloakRepresentations.User;

/**
 * Keycloak's admin API, for the few things our modules need. Every failure is a {@link
 * GeneralException}: {@code KEYCLOAK_CONFLICT} when what we create already exists, {@code
 * KEYCLOAK_REJECTED} when Keycloak refuses the input (a 4xx, e.g. its password policy), {@code
 * KEYCLOAK_UNAVAILABLE} for anything else (a 5xx, a refused login, a timeout, no token). Keycloak's
 * own message is the sub-error; a failure without an answer names only its kind, never a URL.
 */
@RequiredArgsConstructor
public class KeycloakAdmin {

  private final RestClient keycloak;

  /** A machine-to-machine client and its secret. The secret is shown only here. */
  public record IssuedClient(String id, String secret) {}

  /**
   * Reads one realm role: logging in (a token, replaced if refused) and the admin API both work.
   * Throws {@code KEYCLOAK_UNAVAILABLE} if not.
   */
  public void ping() {
    call(() -> keycloak.get().uri(KeycloakConstants.ROLES_ONE).retrieve().toBodilessEntity());
  }

  /**
   * Creates an enabled user with a password and the given required actions (Keycloak's names, e.g.
   * {@link KeycloakConstants#CONFIGURE_TOTP}), done at its next login; returns its id.
   */
  public String createUser(
      String username,
      String email,
      String password,
      boolean emailVerified,
      List<String> requiredActions) {
    return call(() -> newUser(username, email, password, emailVerified, requiredActions));
  }

  private String newUser(
      String username,
      String email,
      String password,
      boolean emailVerified,
      List<String> requiredActions) {
    var user =
        new User(
            username,
            email,
            null,
            true,
            emailVerified,
            requiredActions,
            List.of(new Credential(KeycloakConstants.PASSWORD, password, false)));
    return idOf(created(user, username).getHeaders().getLocation());
  }

  private ResponseEntity<Void> created(User user, String username) {
    return keycloak
        .post()
        .uri(KeycloakConstants.USERS)
        .body(user)
        .retrieve()
        .onStatus(
            status -> status.isSameCodeAs(HttpStatus.CONFLICT),
            (request, response) -> {
              throw new GeneralException(KeycloakExceptionStatus.KEYCLOAK_CONFLICT, username);
            })
        .toBodilessEntity();
  }

  /**
   * Creates an enabled user without a password, with the given realm roles, and has Keycloak e-mail
   * them a link to set one and do the given required actions (Keycloak's names, e.g. {@link
   * KeycloakConstants#CONFIGURE_TOTP}); returns its id. If a role or the e-mail fails, the user is
   * removed again and the call fails.
   */
  public String inviteUser(
      String email, String firstName, List<String> requiredActions, String... realmRoles) {
    var actions =
        Stream.concat(Stream.of(KeycloakConstants.UPDATE_PASSWORD), requiredActions.stream())
            .toList();
    return call(
        () -> {
          // E-mail verified already: the only way in is the link e-mailed to this address.
          var user = new User(email, email, firstName, true, true, actions, null);
          var id = idOf(created(user, email).getHeaders().getLocation());
          try {
            for (var role : realmRoles) realmRole(id, role);
            keycloak
                .put()
                .uri(KeycloakConstants.USER_ACTIONS_EMAIL, id)
                .body(actions)
                .retrieve()
                .toBodilessEntity();
          } catch (RestClientException e) {
            // The e-mail's failure is what the caller learns, even if the clean-up fails too.
            try {
              keycloak.delete().uri(KeycloakConstants.USER, id).retrieve().toBodilessEntity();
            } catch (RestClientException cleanup) {
              e.addSuppressed(cleanup);
            }
            throw e;
          }
          return id;
        });
  }

  /** Removes the user, e.g. an invite whose own record could not be saved. */
  public void deleteUser(String userId) {
    call(() -> keycloak.delete().uri(KeycloakConstants.USER, userId).retrieve().toBodilessEntity());
  }

  /**
   * Disables the user and ends every session at once; the user and its history stay. If ending the
   * sessions fails, the user is already disabled (its refresh tokens are refused): calling again is
   * safe.
   */
  public void disableUser(String userId) {
    call(
        () -> {
          enabled(userId, false);
          return loggedOut(userId);
        });
  }

  /** Ends every session the user has: its refresh tokens are refused from now on. */
  public void logoutUser(String userId) {
    call(() -> loggedOut(userId));
  }

  private ResponseEntity<Void> loggedOut(String userId) {
    return keycloak.post().uri(KeycloakConstants.USER_LOGOUT, userId).retrieve().toBodilessEntity();
  }

  /** Lets a disabled user log in again. */
  public void enableUser(String userId) {
    call(() -> enabled(userId, true));
  }

  /** Keycloak's user update changes only the fields it is sent. */
  private ResponseEntity<Void> enabled(String userId, boolean enabled) {
    return keycloak
        .put()
        .uri(KeycloakConstants.USER, userId)
        .body(new Enabled(enabled))
        .retrieve()
        .toBodilessEntity();
  }

  public void markEmailVerified(String userId) {
    call(
        () -> {
          // Keycloak replaces the whole user on update, so change the field on the full
          // representation.
          // The whole user as Keycloak sent it, every field kept: a tree, not a DTO.
          var user =
              Objects.requireNonNull(
                  keycloak
                      .get()
                      .uri(KeycloakConstants.USER, userId)
                      .retrieve()
                      .body(ObjectNode.class));
          user.put(KeycloakConstants.EMAIL_VERIFIED, true);
          return keycloak
              .put()
              .uri(KeycloakConstants.USER, userId)
              .body(user)
              .retrieve()
              .toBodilessEntity();
        });
  }

  public void grantRealmRole(String userId, String role) {
    call(() -> realmRole(userId, role));
  }

  public void revokeRealmRole(String userId, String role) {
    call(
        () ->
            keycloak
                .method(HttpMethod.DELETE)
                .uri(KeycloakConstants.USER_REALM_ROLES, userId)
                .body(List.of(roleOf(role)))
                .retrieve()
                .toBodilessEntity());
  }

  private Role roleOf(String role) {
    var found = keycloak.get().uri(KeycloakConstants.ROLE, role).retrieve().body(Role.class);
    required(found == null ? null : found.id(), KeycloakConstants.ID);
    return Objects.requireNonNull(found);
  }

  private Object realmRole(String userId, String role) {
    return keycloak
        .post()
        .uri(KeycloakConstants.USER_REALM_ROLES, userId)
        .body(List.of(roleOf(role)))
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * A confidential client that can only use client credentials. Its tokens carry the given realm
   * roles.
   */
  public IssuedClient createConfidentialClient(String clientId, String... realmRoles) {
    return call(() -> newClient(clientId, realmRoles));
  }

  private IssuedClient newClient(String clientId, String... realmRoles) {
    var client = new Client(clientId, true, false, true, false, false);
    var created =
        keycloak
            .post()
            .uri(KeycloakConstants.CLIENTS)
            .body(client)
            .retrieve()
            .onStatus(
                status -> status.isSameCodeAs(HttpStatus.CONFLICT),
                (request, response) -> {
                  throw new GeneralException(KeycloakExceptionStatus.KEYCLOAK_CONFLICT, clientId);
                })
            .toBodilessEntity();
    var id = idOf(created.getHeaders().getLocation());
    if (realmRoles.length > 0) {
      var account =
          keycloak
              .get()
              .uri(KeycloakConstants.CLIENT_SERVICE_ACCOUNT, id)
              .retrieve()
              .body(Identified.class);
      var serviceAccount = required(account == null ? null : account.id(), KeycloakConstants.ID);
      for (var role : realmRoles) realmRole(serviceAccount, role);
    }
    return new IssuedClient(id, secretOf(keycloak.get().uri(KeycloakConstants.CLIENT_SECRET, id)));
  }

  /** Issues a new secret; the old one stops working at once. */
  public String rotateSecret(String clientInternalId) {
    return call(
        () -> secretOf(keycloak.post().uri(KeycloakConstants.CLIENT_SECRET, clientInternalId)));
  }

  /** The input refused (a 4xx but our own login's), or Keycloak unavailable, its message kept. */
  private static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientResponseException e) {
      var status = e.getStatusCode();
      var refused =
          status.is4xxClientError()
              && !status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
              && !status.isSameCodeAs(HttpStatus.FORBIDDEN);
      throw new GeneralException(
              refused
                  ? KeycloakExceptionStatus.KEYCLOAK_REJECTED
                  : KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE)
          .subError(String.valueOf(status.value()), errorMessage(e));
    } catch (RestClientException | OAuth2AuthorizationException e) {
      // Its message names Keycloak's URL: the caller learns only what kind of failure it was.
      throw new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE)
          .subError(e.getClass().getSimpleName(), null);
    }
  }

  /** Keycloak's {@code errorMessage} (admin API) or {@code error_description} (token endpoint). */
  private static @Nullable String errorMessage(RestClientResponseException e) {
    try {
      var body = e.getResponseBodyAs(Failure.class);
      if (body == null) return e.getStatusText();
      var message = body.errorMessage() != null ? body.errorMessage() : body.error();
      return message == null ? e.getStatusText() : message;
    } catch (RuntimeException notJson) {
      return e.getStatusText();
    }
  }

  private static String secretOf(RestClient.RequestHeadersSpec<?> request) {
    var answer = request.retrieve().body(Secret.class);
    return required(answer == null ? null : answer.value(), KeycloakConstants.VALUE);
  }

  /** An answer Keycloak never gives when it works: as good as no answer. */
  private static GeneralException broken(String what) {
    return new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE).subError(null, what);
  }

  /** A field Keycloak always sends; a body without it is a broken answer. */
  private static String required(@Nullable String value, String name) {
    if (value == null) throw broken(KeycloakConstants.MISSING_FIELD.formatted(name));
    return value;
  }

  /** Keycloak answers a create with the new resource's URL; its last segment is the id. */
  private static String idOf(@Nullable URI location) {
    if (location == null) throw broken(KeycloakConstants.MISSING_LOCATION);
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
