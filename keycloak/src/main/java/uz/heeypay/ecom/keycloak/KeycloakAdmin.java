package uz.heeypay.ecom.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.heeypay.ecom.error.GeneralException;

/**
 * Keycloak's admin API, for the few things our modules need. Every failure is a {@link
 * GeneralException}: {@code KEYCLOAK_CONFLICT} when what we create already exists, {@code
 * KEYCLOAK_REJECTED} when Keycloak refuses the input (a 4xx, e.g. its password policy), {@code
 * KEYCLOAK_UNAVAILABLE} for anything else (a 5xx, a refused login, a timeout, no token). Keycloak's
 * own message is the sub-error; a failure without an answer names only its kind, never a URL.
 */
@RequiredArgsConstructor
public class KeycloakAdmin {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

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

  /** Creates an enabled user with a password; returns its id. */
  public String createUser(String username, String email, String password, boolean emailVerified) {
    return call(() -> newUser(username, email, password, emailVerified));
  }

  private String newUser(String username, String email, String password, boolean emailVerified) {
    var user =
        Map.of(
            KeycloakConstants.USERNAME, username,
            KeycloakConstants.EMAIL, email,
            KeycloakConstants.ENABLED, true,
            KeycloakConstants.EMAIL_VERIFIED, emailVerified,
            KeycloakConstants.CREDENTIALS,
                List.of(
                    Map.of(
                        KeycloakConstants.TYPE,
                        KeycloakConstants.PASSWORD,
                        KeycloakConstants.VALUE,
                        password,
                        KeycloakConstants.TEMPORARY,
                        false)));
    return idOf(created(user, username).getHeaders().getLocation());
  }

  private ResponseEntity<Void> created(Map<String, Object> user, String username) {
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
   * Creates an enabled user without a password and has Keycloak e-mail them a link to set one;
   * returns its id. If the e-mail can't be sent, the user is removed again and the call fails.
   */
  public String inviteUser(String email, String firstName) {
    return call(
        () -> {
          var user =
              Map.of(
                  KeycloakConstants.USERNAME,
                  email,
                  KeycloakConstants.EMAIL,
                  email,
                  KeycloakConstants.FIRST_NAME,
                  firstName,
                  KeycloakConstants.ENABLED,
                  true,
                  KeycloakConstants.EMAIL_VERIFIED,
                  true,
                  KeycloakConstants.REQUIRED_ACTIONS,
                  List.of(KeycloakConstants.UPDATE_PASSWORD));
          var id = idOf(created(user, email).getHeaders().getLocation());
          try {
            keycloak
                .put()
                .uri(KeycloakConstants.USER_ACTIONS_EMAIL, id)
                .body(List.of(KeycloakConstants.UPDATE_PASSWORD))
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

  /** Disables the user and ends every session at once; the user and its history stay. */
  public void disableUser(String userId) {
    call(
        () -> {
          // Keycloak replaces the whole user on update, so change the field on the full
          // representation.
          var user =
              Objects.requireNonNull(
                  keycloak.get().uri(KeycloakConstants.USER, userId).retrieve().body(JSON));
          user.put(KeycloakConstants.ENABLED, false);
          keycloak
              .put()
              .uri(KeycloakConstants.USER, userId)
              .body(user)
              .retrieve()
              .toBodilessEntity();
          return keycloak
              .post()
              .uri(KeycloakConstants.USER_LOGOUT, userId)
              .retrieve()
              .toBodilessEntity();
        });
  }

  public void markEmailVerified(String userId) {
    call(
        () -> {
          // Keycloak replaces the whole user on update, so change the field on the full
          // representation.
          var user =
              Objects.requireNonNull(
                  keycloak.get().uri(KeycloakConstants.USER, userId).retrieve().body(JSON));
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

  private Map<String, Object> roleOf(String role) {
    return Objects.requireNonNull(
        keycloak.get().uri(KeycloakConstants.ROLE, role).retrieve().body(JSON));
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
    var client =
        Map.of(
            KeycloakConstants.CLIENT_ID, clientId,
            KeycloakConstants.ENABLED, true,
            KeycloakConstants.PUBLIC_CLIENT, false,
            KeycloakConstants.SERVICE_ACCOUNTS_ENABLED, true,
            KeycloakConstants.STANDARD_FLOW_ENABLED, false,
            KeycloakConstants.DIRECT_ACCESS_GRANTS_ENABLED, false);
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
      var serviceAccount =
          (String)
              field(
                  keycloak
                      .get()
                      .uri(KeycloakConstants.CLIENT_SERVICE_ACCOUNT, id)
                      .retrieve()
                      .body(JSON),
                  KeycloakConstants.ID);
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
      var body = e.getResponseBodyAs(JSON);
      if (body == null) return e.getStatusText();
      var message =
          body.getOrDefault(KeycloakConstants.ERROR_MESSAGE, body.get(KeycloakConstants.ERROR));
      return message == null ? e.getStatusText() : message.toString();
    } catch (RuntimeException notJson) {
      return e.getStatusText();
    }
  }

  private static String secretOf(RestClient.RequestHeadersSpec<?> request) {
    var answer = request.attribute(KeycloakConstants.NO_LOGGED_BODIES, true).retrieve().body(JSON);
    return (String) field(answer, KeycloakConstants.VALUE);
  }

  /** An answer Keycloak never gives when it works: as good as no answer. */
  private static GeneralException broken(String what) {
    return new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE).subError(null, what);
  }

  /** A field Keycloak always sends; a body without it is a broken answer. */
  private static Object field(@Nullable Map<String, Object> body, String name) {
    var value = body == null ? null : body.get(name);
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
