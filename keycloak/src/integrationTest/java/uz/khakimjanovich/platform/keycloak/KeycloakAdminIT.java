package uz.khakimjanovich.platform.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.observability.ObservabilityAutoConfiguration;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class KeycloakAdminIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  static final Network NETWORK = Network.newNetwork();

  @Container
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("mailpit"))
          .withNetwork(NETWORK)
          .withNetworkAliases("mailpit")
          .withExposedPorts(8025);

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
          .withNetwork(NETWORK)
          .withCommand("start-dev", "--import-realm")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("demo-realm.json"),
              "/opt/keycloak/data/import/demo-realm.json")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/demo").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

  static ConfigurableApplicationContext context;
  static KeycloakAdmin admin;

  @BeforeAll
  static void client() {
    context =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .properties(
                "platform.keycloak.url=" + url(),
                "platform.keycloak.realm=demo",
                "platform.keycloak.client-id=demo-backend",
                "platform.keycloak.client-secret=local-only",
                // No setting has a default; this test starts no collector, and traces nothing.
                "TRACING_SAMPLING=0.0",
                "OTLP_TRACES_ENDPOINT=http://otlp.not-started.invalid/v1/traces",
                "OTLP_METRICS_ENDPOINT=http://otlp.not-started.invalid/v1/metrics",
                "OTLP_LOGS_ENDPOINT=http://otlp.not-started.invalid/v1/logs")
            .run();
    admin = context.getBean(KeycloakAdmin.class);
  }

  @AfterAll
  static void close() {
    context.close();
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    RestClientAutoConfiguration.class,
    ObservabilityAutoConfiguration.class,
    KeycloakAutoConfiguration.class
  })
  static class App {}

  @Test
  void aUserGetsAVerifiedEmailAndARoleAndCanLogIn() {
    var email = UUID.randomUUID() + "@example.com";
    var id = admin.createUser(email, email, "correct-horse-42", false, List.of());
    admin.markEmailVerified(id);
    admin.grantRealmRole(id, "partner");

    var token = passwordToken(email, "correct-horse-42");
    assertThat(token).isNotBlank();
    assertThatThrownBy(() -> admin.createUser(email, email, "another-password", false, List.of()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_CONFLICT));
  }

  @Test
  void aClientSecretWorksUntilItIsRotated(CapturedOutput output) {
    var clientId = "partner-" + UUID.randomUUID().toString().substring(0, 8);
    var issued = admin.createConfidentialClient(clientId, "partner");
    assertThat(clientCredentials(clientId, issued.secret())).isEqualTo(200);
    var token = clientToken(clientId, issued.secret());
    var claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    assertThat(claims).as("the client's tokens carry its role").contains("\"partner\"");

    var rotated = admin.rotateSecret(issued.id());
    assertThat(clientCredentials(clientId, issued.secret())).isEqualTo(401);
    assertThat(clientCredentials(clientId, rotated)).isEqualTo(200);
    assertThat(output)
        .as("the calls are logged, the secrets masked")
        .contains("/client-secret | ")
        .doesNotContain(issued.secret(), rotated);
  }

  @Test
  void anInvitedUserGetsAnEmailToSetAPassword() {
    var email = UUID.randomUUID() + "@example.com";
    var id = admin.inviteUser(email, "Aziza", List.of(), "merchant-operator");

    var user = adminGet("/users/" + id);
    assertThat(user)
        .containsEntry("firstName", "Aziza")
        .containsEntry("enabled", true)
        .containsEntry("requiredActions", List.of("UPDATE_PASSWORD"));
    var mails =
        RestClient.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025))
            .get()
            .uri("/api/v1/search?query=to:{email}", email)
            .retrieve()
            .body(JSON);
    assertThat(mails).containsEntry("messages_count", 1);
    assertThat(adminGetList("/users/" + id + "/role-mappings/realm"))
        .extracting(role -> role.get("name"))
        .contains("merchant-operator");
    assertThatThrownBy(() -> admin.inviteUser(email, "Aziza", List.of()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_CONFLICT));

    admin.deleteUser(id);
    assertThat(admin.inviteUser(email, "Aziza", List.of()))
        .as("the e-mail is free again")
        .isNotEqualTo(id);
  }

  @Test
  void aCreatedUserCarriesTheRequiredActionsAsked() {
    var email = UUID.randomUUID() + "@example.com";
    var id =
        admin.createUser(
            email, email, "correct-horse-42", true, List.of(KeycloakConstants.CONFIGURE_TOTP));

    assertThat(adminGet("/users/" + id))
        .containsEntry("requiredActions", List.of(KeycloakConstants.CONFIGURE_TOTP));
  }

  @Test
  void anInvitedUserIsEmailedEveryRequiredActionAsked() {
    var email = UUID.randomUUID() + "@example.com";
    var id =
        admin.inviteUser(
            email, "Aziza", List.of(KeycloakConstants.CONFIGURE_TOTP), "merchant-operator");

    assertThat(adminGet("/users/" + id))
        .extractingByKey("requiredActions", InstanceOfAssertFactories.LIST)
        .as("Keycloak keeps them in no particular order")
        .containsExactlyInAnyOrder(
            KeycloakConstants.UPDATE_PASSWORD, KeycloakConstants.CONFIGURE_TOTP);
    var mails =
        RestClient.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025))
            .get()
            .uri("/api/v1/search?query=to:{email}", email)
            .retrieve()
            .body(JSON);
    assertThat(mails).containsEntry("messages_count", 1);
  }

  @Test
  void aRoleIsSwappedAndADisabledUserLosesItsSessionsUntilEnabled() {
    var email = UUID.randomUUID() + "@example.com";
    var id = admin.createUser(email, email, "correct-horse-42", true, List.of());
    admin.grantRealmRole(id, "merchant-operator");
    admin.revokeRealmRole(id, "merchant-operator");
    admin.grantRealmRole(id, "merchant-observer");
    var claims = claims(passwordToken(email, "correct-horse-42"));
    assertThat(claims).contains("\"merchant-observer\"").doesNotContain("merchant-operator");

    var refresh = (String) passwordGrant(email, "correct-horse-42").get("refresh_token");
    admin.disableUser(id);
    assertThat(adminGet("/users/" + id)).containsEntry("enabled", false);
    assertThat(refreshStatus(refresh)).as("the session ended").isEqualTo(400);
    assertThat(passwordStatus(email, "correct-horse-42")).isEqualTo(400);

    admin.enableUser(id);
    assertThat(adminGet("/users/" + id))
        .as("only enabled changed")
        .containsEntry("enabled", true)
        .containsEntry("email", email);
    assertThat(passwordStatus(email, "correct-horse-42")).isEqualTo(200);
  }

  private static String claims(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
  }

  private static Map<String, Object> adminGet(String path) {
    return adminApi(path).body(JSON);
  }

  private static List<Map<String, Object>> adminGetList(String path) {
    return adminApi(path).body(new ParameterizedTypeReference<>() {});
  }

  private static RestClient.ResponseSpec adminApi(String path) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", "admin");
    form.add("password", "test");
    var token =
        RestClient.create()
            .post()
            .uri(url() + "/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JSON)
            .get("access_token");
    return RestClient.create()
        .get()
        .uri(url() + "/admin/realms/demo" + path)
        .header("Authorization", "Bearer " + token)
        .retrieve();
  }

  private static Map<String, Object> passwordGrant(String username, String password) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "demo-portal");
    form.add("username", username);
    form.add("password", password);
    return RestClient.create()
        .post()
        .uri(url() + "/realms/demo/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(JSON);
  }

  private static int passwordStatus(String username, String password) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "demo-portal");
    form.add("username", username);
    form.add("password", password);
    return tokenStatus(form);
  }

  private static int refreshStatus(String refreshToken) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "refresh_token");
    form.add("client_id", "demo-portal");
    form.add("refresh_token", refreshToken);
    return tokenStatus(form);
  }

  private static int tokenStatus(LinkedMultiValueMap<String, String> form) {
    return RestClient.create()
        .post()
        .uri(url() + "/realms/demo/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .exchange((req, res) -> res.getStatusCode().value());
  }

  private static String passwordToken(String username, String password) {
    return (String) passwordGrant(username, password).get("access_token");
  }

  private static String clientToken(String clientId, String secret) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    return (String)
        RestClient.create()
            .post()
            .uri(url() + "/realms/demo/protocol/openid-connect/token")
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
        .uri(url() + "/realms/demo/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .exchange((req, res) -> res.getStatusCode().value());
  }

  private static String url() {
    return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
  }
}
