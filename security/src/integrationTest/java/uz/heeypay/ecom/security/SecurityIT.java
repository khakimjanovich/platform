package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * The library against a real Keycloak, with real password and client-credentials tokens: roles per
 * route, everything else refused, tokens that must name our API, and public routes that ignore
 * whatever token comes with them.
 */
@SpringBootTest(
    classes = SecurityIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.security.oauth2.resourceserver.jwt.audiences=ecom-api",
      "ecom.security.public-routes[0]=/public/**",
      "ecom.security.public-routes[1]=/actuator/health/**",
      "ecom.security.roles[/admin/**]=ADMIN",
      "ecom.security.roles[/partner/**]=PARTNER"
    })
@Testcontainers
class SecurityIT {

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

  @DynamicPropertySource
  static void issuer(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak() + "/realms/test");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {
    @RestController
    static class Endpoints {
      @GetMapping({"/admin/report", "/partner/me", "/public/page", "/unlisted"})
      String ok() {
        return "ok";
      }
    }
  }

  @LocalServerPort int port;

  @Test
  void eachRouteNeedsItsRole() {
    call("/admin/report", password("aziz")).expectStatus().isOk();
    call("/partner/me", password("malika")).expectStatus().isOk();
    call("/admin/report", password("malika"))
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void aRouteNobodyListedIsRefused() {
    call("/unlisted", password("aziz")).expectStatus().isForbidden();
  }

  @Test
  void aTokenForAnotherAudienceIsA401() {
    var token = clientCredentials("outsider", "outsider-secret");
    assertThat(claims(token)).doesNotContain("ecom-api");
    call("/admin/report", token)
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  void aPublicRouteIgnoresATokenThatNoLongerValidates() {
    var stale = tampered(password("aziz"));
    call("/admin/report", stale).expectStatus().isUnauthorized();
    call("/public/page", stale).expectStatus().isOk();
    call("/actuator/health", stale).expectStatus().isOk();
  }

  private RestTestClient.ResponseSpec call(String path, String token) {
    return RestTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange();
  }

  /** The same token with one claim changed: its signature no longer matches. */
  private static String tampered(String token) {
    var parts = token.split("\\.");
    var payload = claims(token).replace("\"aziz\"", "\"malik\"");
    return parts[0]
        + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
        + "."
        + parts[2];
  }

  private static String claims(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
  }

  private static String password(String username) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "portal");
    form.add("username", username);
    form.add("password", "local-only");
    return token(form);
  }

  private static String clientCredentials(String clientId, String secret) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    return token(form);
  }

  private static String token(LinkedMultiValueMap<String, String> form) {
    return (String)
        RestClient.create()
            .post()
            .uri(keycloak() + "/realms/test/protocol/openid-connect/token")
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
