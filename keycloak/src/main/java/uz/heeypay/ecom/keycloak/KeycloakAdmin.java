package uz.heeypay.ecom.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.heeypay.ecom.error.GeneralException;

/**
 * Keycloak's admin API, for the few things our modules need. Every failure is a {@link
 * GeneralException}: {@code KEYCLOAK_CONFLICT} when what we create already exists, {@code
 * KEYCLOAK_UNAVAILABLE} for anything else (an error status, a timeout, no token), with Keycloak's
 * own message as a sub-error.
 */
@RequiredArgsConstructor
public class KeycloakAdmin {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  private final RestClient keycloak;

  /** A machine-to-machine client and its secret. The secret is shown only here. */
  public record IssuedClient(String id, String secret) {}

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
    var created =
        keycloak
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
    return idOf(created.getHeaders().getLocation());
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

  private Object realmRole(String userId, String role) {
    var representation =
        Objects.requireNonNull(
            keycloak.get().uri(KeycloakConstants.ROLE, role).retrieve().body(JSON));
    return keycloak
        .post()
        .uri(KeycloakConstants.USER_REALM_ROLES, userId)
        .body(List.of(representation))
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

  /** Any failure but a conflict is Keycloak being unavailable, with its own message kept. */
  private static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientResponseException e) {
      throw new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE)
          .subError(String.valueOf(e.getStatusCode().value()), errorMessage(e));
    } catch (RestClientException | OAuth2AuthorizationException e) {
      throw new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE)
          .subError(e.getClass().getSimpleName(), e.getMessage());
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
