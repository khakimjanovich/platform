package uz.heeypay.ecom.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * A service calls another through an HTTP service group with a client-credentials token from a real
 * Keycloak; the callee (this app, secured by the library) checks its role. A refused token is
 * dropped and the call tried once more, and a rotated secret works once the property has it.
 */
@SpringBootTest(
    classes = ServiceTokensIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
      "spring.security.oauth2.resourceserver.jwt.audiences=ecom-api",
      "ecom.security.roles[/adapter/**]=ADAPTER_CLIENT",
      "spring.security.oauth2.client.registration.app.client-id=ecom-app",
      "spring.security.oauth2.client.registration.app.client-secret=app-secret",
      "spring.security.oauth2.client.registration.stranger.client-id=stranger",
      "spring.security.oauth2.client.registration.stranger.client-secret=stranger-secret",
      "ecom.security.service-tokens.groups.adapter=app",
      "ecom.security.service-tokens.groups.stranger=stranger",
      "ecom.security.service-tokens.groups.flaky=app"
    })
@Testcontainers
class ServiceTokensIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};
  private static final int PORT = freePort();

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

  @RegisterExtension
  static final WireMockExtension FLAKY =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void services(DynamicPropertyRegistry registry) {
    registry.add("server.port", () -> PORT);
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> realm());
    for (var registration : List.of("app", "stranger")) {
      var prefix = "spring.security.oauth2.client.registration." + registration;
      registry.add(prefix + ".authorization-grant-type", () -> "client_credentials");
      registry.add(prefix + ".provider", () -> "keycloak");
    }
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", ServiceTokensIT::tokenUri);
    registry.add("spring.http.serviceclient.adapter.base-url", () -> "http://localhost:" + PORT);
    registry.add("spring.http.serviceclient.stranger.base-url", () -> "http://localhost:" + PORT);
    registry.add("spring.http.serviceclient.flaky.base-url", FLAKY::baseUrl);
  }

  interface AdapterClient {
    @GetExchange("/adapter/ping")
    String ping();
  }

  interface StrangerClient {
    @GetExchange("/adapter/ping")
    String ping();
  }

  interface FlakyClient {
    @GetExchange("/adapter/ping")
    String ping();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @ImportHttpServices(group = "adapter", types = AdapterClient.class)
  @ImportHttpServices(group = "stranger", types = StrangerClient.class)
  @ImportHttpServices(group = "flaky", types = FlakyClient.class)
  static class App {
    /** The callee: what an adapter serves, for callers with role ADAPTER_CLIENT only. */
    @RestController
    static class Adapter {
      @GetMapping("/adapter/ping")
      String ping() {
        return "pong";
      }
    }
  }

  /** A caller only: the rotated secret's own registration, started on its own. */
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @ImportHttpServices(group = "adapter", types = AdapterClient.class)
  static class Caller {}

  @Autowired AdapterClient adapter;
  @Autowired StrangerClient stranger;
  @Autowired FlakyClient flaky;

  @Test
  void aGroupCallsWithItsServiceToken() {
    assertThat(adapter.ping()).isEqualTo("pong");
  }

  @Test
  void theCalleeChecksTheRole() {
    assertThatThrownBy(stranger::ping).isInstanceOf(HttpClientErrorException.Forbidden.class);
  }

  @Test
  void aRefusedTokenIsDroppedAndTheCallTriedOnceMore() {
    FLAKY.stubFor(get(urlEqualTo("/adapter/ping")).willReturn(aResponse().withStatus(401)));
    assertThatThrownBy(flaky::ping).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    FLAKY.verify(
        2,
        getRequestedFor(urlEqualTo("/adapter/ping"))
            .withHeader("Authorization", matching("Bearer .+")));
  }

  @Test
  void aRotatedSecretWorksOnceThePropertyHasIt() {
    var rotated = rotateSecret("rotating");
    assertThatThrownBy(() -> ping("rotating-secret")).isNotNull();
    assertThat(ping(rotated)).isEqualTo("pong");
  }

  /** A fresh caller with this secret for client "rotating", as after a redeploy. */
  private static String ping(String secret) {
    var prefix = "spring.security.oauth2.client.registration.rotating.";
    try (var context =
        new SpringApplicationBuilder(Caller.class)
            .web(WebApplicationType.NONE)
            .properties(
                prefix + "client-id=rotating",
                prefix + "client-secret=" + secret,
                prefix + "authorization-grant-type=client_credentials",
                prefix + "provider=keycloak",
                "spring.security.oauth2.client.provider.keycloak.token-uri=" + tokenUri(),
                "ecom.security.service-tokens.groups.adapter=rotating",
                "spring.http.serviceclient.adapter.base-url=http://localhost:" + PORT)
            .run()) {
      return context.getBean(AdapterClient.class).ping();
    }
  }

  /** As the bank's Keycloak admin does it: a new secret, the old one stops working at once. */
  private static String rotateSecret(String clientId) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", "admin");
    form.add("password", "test");
    var admin =
        (String)
            RestClient.create()
                .post()
                .uri(keycloak() + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JSON)
                .get("access_token");
    var api =
        RestClient.builder()
            .baseUrl(keycloak() + "/admin/realms/test")
            .defaultHeader("Authorization", "Bearer " + admin)
            .build();
    var id =
        api.get()
            .uri("/clients?clientId={id}", clientId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .getFirst()
            .get("id");
    return (String)
        api.post().uri("/clients/{id}/client-secret", id).retrieve().body(JSON).get("value");
  }

  private static String keycloak() {
    return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
  }

  private static String realm() {
    return keycloak() + "/realms/test";
  }

  private static String tokenUri() {
    return realm() + "/protocol/openid-connect/token";
  }

  private static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
