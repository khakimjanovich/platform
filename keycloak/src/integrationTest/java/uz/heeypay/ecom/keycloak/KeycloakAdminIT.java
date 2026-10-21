package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
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
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
          .withCommand("start-dev", "--import-realm")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("test-realm.json"),
              "/opt/keycloak/data/import/test-realm.json")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/test").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

  static ConfigurableApplicationContext context;
  static KeycloakAdmin admin;

  /** The library as an app gets it: auto-configured from ecom.keycloak.* alone. */
  @BeforeAll
  static void client() {
    context =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .properties(
                "ecom.keycloak.url=" + url(),
                "ecom.keycloak.realm=test",
                "ecom.keycloak.client-id=backend",
                "ecom.keycloak.client-secret=test-secret")
            .run();
    admin = context.getBean(KeycloakAdmin.class);
  }

  @AfterAll
  static void close() {
    context.close();
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(KeycloakAutoConfiguration.class)
  static class App {}

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
    var issued = admin.createConfidentialClient(clientId, "partner");
    assertThat(clientCredentials(clientId, issued.secret())).isEqualTo(200);
    var token = clientToken(clientId, issued.secret());
    var claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    assertThat(claims).as("the client's tokens carry its role").contains("\"partner\"");

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

  private static String clientToken(String clientId, String secret) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
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
