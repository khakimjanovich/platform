package uz.khakimjanovich.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * A Keycloak that speaks HTTPS only, with a certificate from the app's own CA (tls/, which the JVM
 * doesn't trust): the service token is fetched and the issuer's keys are read over the bundle that
 * trusts that CA, so a call with the token gets through.
 */
@SpringBootTest(
    classes = IssuerOverTlsIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
      "spring.ssl.bundle.pem.keycloak.truststore.certificate=classpath:tls/ca.pem",
      "platform.security.ssl-bundle=keycloak",
      "platform.security.service-tokens.ssl-bundle=keycloak",
      "platform.security.roles[/adapter/**]=ADAPTER_CLIENT",
      "spring.security.oauth2.client.registration.app.client-id=demo-app",
      "spring.security.oauth2.client.registration.app.client-secret=app-secret",
      "spring.security.oauth2.client.registration.app.authorization-grant-type=client_credentials",
      "spring.security.oauth2.client.registration.app.provider=keycloak",
      "platform.security.service-tokens.groups.adapter=app"
    })
@Testcontainers
class IssuerOverTlsIT {

  private static final int PORT = freePort();

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
          .withCommand(
              "start-dev",
              "--import-realm",
              "--http-enabled=false",
              "--https-certificate-file=/opt/keycloak/conf/tls.pem",
              "--https-certificate-key-file=/opt/keycloak/conf/tls-key.pem")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("demo-realm.json"),
              "/opt/keycloak/data/import/demo-realm.json")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("tls/keycloak.pem"), "/opt/keycloak/conf/tls.pem")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("tls/keycloak-key.pem"),
              "/opt/keycloak/conf/tls-key.pem")
          .withExposedPorts(8443)
          .waitingFor(
              Wait.forHttps("/realms/demo")
                  .forPort(8443)
                  .allowInsecure()
                  .withStartupTimeout(Duration.ofMinutes(3)));

  @DynamicPropertySource
  static void services(DynamicPropertyRegistry registry) {
    registry.add("server.port", () -> PORT);
    // A list comes whole from one property source.
    registry.add("platform.security.issuers[0].uri", IssuerOverTlsIT::realm);
    registry.add("platform.security.issuers[0].audience", () -> "demo-api");
    registry.add("platform.security.issuers[0].roles", () -> "adapter_client");
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        () -> realm() + "/protocol/openid-connect/token");
    registry.add("spring.http.serviceclient.adapter.base-url", () -> "http://localhost:" + PORT);
  }

  interface AdapterClient {
    @GetExchange("/adapter/ping")
    String ping();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @ImportHttpServices(group = "adapter", types = AdapterClient.class)
  static class App {
    @RestController
    static class Adapter {
      @GetMapping("/adapter/ping")
      String ping() {
        return "pong";
      }
    }
  }

  @Autowired AdapterClient adapter;

  @Test
  void theTokenAndTheIssuersKeysComeOverTheBundlesTrust() {
    assertThat(adapter.ping()).isEqualTo("pong");
  }

  /** localhost, the name on Keycloak's certificate. */
  private static String realm() {
    return "https://localhost:" + KEYCLOAK.getMappedPort(8443) + "/realms/demo";
  }

  private static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
