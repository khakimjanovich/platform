package uz.khakimjanovich.platform.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Trusted issuers against a real Keycloak with three realms: two trusted, each for its own clients,
 * and one not. A token is accepted only from a trusted issuer and a client listed for it; the
 * route's role is checked after that.
 */
@SpringBootTest(
    classes = TrustedIssuersIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "platform.security.roles[/adapter/**]=ADAPTER_CLIENT")
@Testcontainers
class TrustedIssuersIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
          .withCommand("start-dev", "--import-realm")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
          .withCopyToContainer(realm("demo"), "/opt/keycloak/data/import/demo-realm.json")
          .withCopyToContainer(realm("other"), "/opt/keycloak/data/import/other-realm.json")
          .withCopyToContainer(realm("rogue"), "/opt/keycloak/data/import/rogue-realm.json")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/rogue")
                  .forPort(8080)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  @DynamicPropertySource
  static void issuers(DynamicPropertyRegistry registry) {
    registry.add("platform.security.issuers[0].uri", () -> keycloak() + "/realms/demo");
    registry.add("platform.security.issuers[0].audience", () -> "demo-api");
    registry.add("platform.security.issuers[0].clients", () -> "demo-app,stranger");
    registry.add("platform.security.issuers[1].uri", () -> keycloak() + "/realms/other");
    registry.add("platform.security.issuers[1].audience", () -> "demo-api");
    registry.add("platform.security.issuers[1].clients", () -> "demo-app");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {

    @RestController
    static class Endpoints {
      @GetMapping("/adapter/call")
      String ok() {
        return "ok";
      }
    }
  }

  @LocalServerPort int port;

  @Test
  void aListedClientOfEitherTrustedIssuerIsLetIn() {
    call(token("demo", "demo-app", "app-secret")).expectStatus().isOk();
    call(token("other", "demo-app", "app-secret")).expectStatus().isOk();
  }

  @Test
  void aClientNotListedForItsIssuerIsA401EvenWithTheRole() {
    call(token("demo", "rotating", "rotating-secret"))
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHORIZED");
    call(token("other", "stranger", "stranger-secret")).expectStatus().isUnauthorized();
  }

  @Test
  void anIssuerNotTrustedIsA401() {
    call(token("rogue", "demo-app", "app-secret")).expectStatus().isUnauthorized();
  }

  @Test
  void aListedClientWithoutTheRouteRoleIsA403() {
    call(token("demo", "stranger", "stranger-secret"))
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("FORBIDDEN");
  }

  private RestTestClient.ResponseSpec call(String token) {
    return RestTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri("/adapter/call")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange();
  }

  /** The demo realm under another name: the same clients and secrets, its own keys. */
  private static Transferable realm(String name) {
    try {
      var demo =
          new ClassPathResource("demo-realm.json").getContentAsString(StandardCharsets.UTF_8);
      return Transferable.of(
          demo.replaceFirst("\"realm\": \"demo\"", "\"realm\": \"" + name + "\""));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String token(String realm, String clientId, String secret) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    return (String)
        RestClient.create()
            .post()
            .uri(keycloak() + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JSON)
            .get("access_token");
  }

  private static String keycloak() {
    return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
  }
}
