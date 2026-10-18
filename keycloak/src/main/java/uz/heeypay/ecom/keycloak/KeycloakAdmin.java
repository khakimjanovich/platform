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
            KeycloakConstantUtils.USERNAME, username,
            KeycloakConstantUtils.EMAIL, email,
            KeycloakConstantUtils.ENABLED, true,
            KeycloakConstantUtils.EMAIL_VERIFIED, emailVerified,
            KeycloakConstantUtils.CREDENTIALS,
                List.of(
                    Map.of(
                        KeycloakConstantUtils.TYPE,
                        KeycloakConstantUtils.PASSWORD,
                        KeycloakConstantUtils.VALUE,
                        password,
                        KeycloakConstantUtils.TEMPORARY,
                        false)));
    var created =
        keycloak
            .post()
            .uri(KeycloakConstantUtils.USERS)
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
    var user = keycloak.get().uri(KeycloakConstantUtils.USER, userId).retrieve().body(JSON);
    user.put(KeycloakConstantUtils.EMAIL_VERIFIED, true);
    keycloak.put().uri(KeycloakConstantUtils.USER, userId).body(user).retrieve().toBodilessEntity();
  }

  public void grantRealmRole(String userId, String role) {
    var representation = keycloak.get().uri(KeycloakConstantUtils.ROLE, role).retrieve().body(JSON);
    keycloak
        .post()
        .uri(KeycloakConstantUtils.USER_REALM_ROLES, userId)
        .body(List.of(representation))
        .retrieve()
        .toBodilessEntity();
  }

  /** A confidential client that can only use client credentials. */
  public IssuedClient createConfidentialClient(String clientId) {
    var client =
        Map.of(
            KeycloakConstantUtils.CLIENT_ID, clientId,
            KeycloakConstantUtils.ENABLED, true,
            KeycloakConstantUtils.PUBLIC_CLIENT, false,
            KeycloakConstantUtils.SERVICE_ACCOUNTS_ENABLED, true,
            KeycloakConstantUtils.STANDARD_FLOW_ENABLED, false,
            KeycloakConstantUtils.DIRECT_ACCESS_GRANTS_ENABLED, false);
    var created =
        keycloak
            .post()
            .uri(KeycloakConstantUtils.CLIENTS)
            .body(client)
            .retrieve()
            .onStatus(
                status -> status.isSameCodeAs(HttpStatus.CONFLICT),
                (request, response) -> {
                  throw new KeycloakConflictException(clientId);
                })
            .toBodilessEntity();
    var id = idOf(created.getHeaders().getLocation());
    return new IssuedClient(
        id, secretOf(keycloak.get().uri(KeycloakConstantUtils.CLIENT_SECRET, id)));
  }

  /** Issues a new secret; the old one stops working at once. */
  public String rotateSecret(String clientInternalId) {
    return secretOf(keycloak.post().uri(KeycloakConstantUtils.CLIENT_SECRET, clientInternalId));
  }

  private static String secretOf(RestClient.RequestHeadersSpec<?> request) {
    return (String) request.retrieve().body(JSON).get(KeycloakConstantUtils.VALUE);
  }

  /** Keycloak answers a create with the new resource's URL; its last segment is the id. */
  private static String idOf(URI location) {
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
