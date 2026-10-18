package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** The admin client against a real Keycloak: users, roles, and client secrets that rotate. */
@Testcontainers
class KeycloakAdminIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.4.0")
          .withCommand("start-dev", "--import-realm")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("test-realm.json"),
              "/opt/keycloak/data/import/test-realm.json")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/test").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

  static KeycloakAdmin admin;

  @BeforeAll
  static void client() {
    admin =
        new KeycloakAutoConfiguration()
            .keycloakAdmin(new KeycloakProperties(url(), "test", "backend", "test-secret"));
  }

  @Test
  void aUserGetsAVerifiedEmailAndARoleAndCanLogIn() {
    var email = UUID.randomUUID() + "@example.uz";
    var id = admin.createUser(email, email, "correct-horse-42", false);
    admin.markEmailVerified(id);
    admin.grantRealmRole(id, "partner");

    var token = passwordToken(email, "correct-horse-42");
    assertThat(token).isNotBlank();
    assertThatThrownBy(() -> admin.createUser(email, email, "another-password", false))
        .isInstanceOf(KeycloakConflictException.class);
  }

  @Test
  void aClientSecretWorksUntilItIsRotated() {
    var clientId = "partner-" + UUID.randomUUID().toString().substring(0, 8);
    var issued = admin.createConfidentialClient(clientId);
    assertThat(clientCredentials(clientId, issued.secret())).isEqualTo(200);

    var rotated = admin.rotateSecret(issued.id());
    assertThat(clientCredentials(clientId, issued.secret())).isEqualTo(401);
    assertThat(clientCredentials(clientId, rotated)).isEqualTo(200);
  }

  private static String passwordToken(String username, String password) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "portal");
    form.add("username", username);
    form.add("password", password);
    return (String)
        RestClient.create()
            .post()
            .uri(url() + "/realms/test/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JSON)
            .get("access_token");
  }

  private static int clientCredentials(String clientId, String secret) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    return RestClient.create()
        .post()
        .uri(url() + "/realms/test/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .exchange((req, res) -> res.getStatusCode().value());
  }

  private static String url() {
    return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
  }
}
