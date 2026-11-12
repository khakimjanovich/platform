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

@RequiredArgsConstructor
public class KeycloakAdmin {

  private final RestClient keycloak;

  public record IssuedClient(String id, String secret) {}

  public void ping() {
    call(() -> keycloak.get().uri(KeycloakConstants.ROLES_ONE).retrieve().toBodilessEntity());
  }

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

  public void deleteUser(String userId) {
    call(() -> keycloak.delete().uri(KeycloakConstants.USER, userId).retrieve().toBodilessEntity());
  }

  public void disableUser(String userId) {
    call(
        () -> {
          enabled(userId, false);
          return loggedOut(userId);
        });
  }

  public void logoutUser(String userId) {
    call(() -> loggedOut(userId));
  }

  private ResponseEntity<Void> loggedOut(String userId) {
    return keycloak.post().uri(KeycloakConstants.USER_LOGOUT, userId).retrieve().toBodilessEntity();
  }

  public void enableUser(String userId) {
    call(() -> enabled(userId, true));
  }

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

  public String rotateSecret(String clientInternalId) {
    return call(
        () -> secretOf(keycloak.post().uri(KeycloakConstants.CLIENT_SECRET, clientInternalId)));
  }

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

  private static GeneralException broken(String what) {
    return new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE).subError(null, what);
  }

  private static String required(@Nullable String value, String name) {
    if (value == null) throw broken(KeycloakConstants.MISSING_FIELD.formatted(name));
    return value;
  }

  private static String idOf(@Nullable URI location) {
    if (location == null) throw broken(KeycloakConstants.MISSING_LOCATION);
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
