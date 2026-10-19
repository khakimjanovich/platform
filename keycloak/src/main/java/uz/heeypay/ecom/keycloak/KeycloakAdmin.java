package uz.heeypay.ecom.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** Keycloak's admin API, for the few things our modules need. */
@RequiredArgsConstructor
public class KeycloakAdmin {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  private final RestClient keycloak;

  /** A machine-to-machine client and its secret. The secret is shown only here. */
  public record IssuedClient(String id, String secret) {}

  /** Creates an enabled user with a password; returns its id. */
  public String createUser(String username, String email, String password, boolean emailVerified) {
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
                  throw new KeycloakConflictException(username);
                })
            .toBodilessEntity();
    return idOf(created.getHeaders().getLocation());
  }

  public void markEmailVerified(String userId) {
    // Keycloak replaces the whole user on update, so change the field on the full representation.
    var user = keycloak.get().uri(KeycloakConstants.USER, userId).retrieve().body(JSON);
    user.put(KeycloakConstants.EMAIL_VERIFIED, true);
    keycloak.put().uri(KeycloakConstants.USER, userId).body(user).retrieve().toBodilessEntity();
  }

  public void grantRealmRole(String userId, String role) {
    var representation = keycloak.get().uri(KeycloakConstants.ROLE, role).retrieve().body(JSON);
    keycloak
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
                  throw new KeycloakConflictException(clientId);
                })
            .toBodilessEntity();
    var id = idOf(created.getHeaders().getLocation());
    if (realmRoles.length > 0) {
      var serviceAccount =
          (String)
              keycloak
                  .get()
                  .uri(KeycloakConstants.CLIENT_SERVICE_ACCOUNT, id)
                  .retrieve()
                  .body(JSON)
                  .get(KeycloakConstants.ID);
      for (var role : realmRoles) grantRealmRole(serviceAccount, role);
    }
    return new IssuedClient(id, secretOf(keycloak.get().uri(KeycloakConstants.CLIENT_SECRET, id)));
  }

  /** Issues a new secret; the old one stops working at once. */
  public String rotateSecret(String clientInternalId) {
    return secretOf(keycloak.post().uri(KeycloakConstants.CLIENT_SECRET, clientInternalId));
  }

  private static String secretOf(RestClient.RequestHeadersSpec<?> request) {
    return (String) request.retrieve().body(JSON).get(KeycloakConstants.VALUE);
  }

  /** Keycloak answers a create with the new resource's URL; its last segment is the id. */
  private static String idOf(URI location) {
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
