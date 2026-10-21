package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.heeypay.ecom.error.GeneralException;

/** No raw HTTP failure escapes: a conflict, or Keycloak unavailable with its own message. */
class KeycloakAdminTest {

  private final RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final KeycloakAdmin admin = new KeycloakAdmin(builder.build());

  @Test
  void anExistingUserIsAConflict() {
    server.expect(requestTo("http://keycloak/users")).andRespond(withStatus(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> admin.createUser("aziz", "aziz@example.uz", "pw", false))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_CONFLICT);
              assertThat(e.getParams()).containsExactly("aziz");
            });
  }

  @Test
  void aCreateAnsweredWithoutALocationIsUnavailable() {
    server.expect(requestTo("http://keycloak/users")).andRespond(withStatus(HttpStatus.CREATED));
    assertThatThrownBy(() -> admin.createUser("aziz", "aziz@example.uz", "pw", false))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
  }

  @Test
  void anErrorStatusIsUnavailableWithKeycloaksMessage() {
    server
        .expect(requestTo("http://keycloak/roles/partner"))
        .andRespond(
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorMessage\":\"unknown_error\"}"));
    assertThatThrownBy(() -> admin.grantRealmRole("user-1", "partner"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE);
              assertThat(e.getSubErrors())
                  .containsExactly(new GeneralException.SubError("500", "unknown_error"));
            });
  }

  @Test
  void noAnswerIsUnavailable() {
    server
        .expect(requestTo("http://keycloak/clients/c-1/client-secret"))
        .andRespond(withException(new IOException("Connection refused")));
    assertThatThrownBy(() -> admin.rotateSecret("c-1"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
  }

  /** A Keycloak that accepts the connection and never answers: the read timeout ends the wait. */
  @Test
  void theReadTimeoutIsApplied() throws IOException {
    try (var silent = new ServerSocket(0)) {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "ecom.keycloak.url=http://localhost:" + silent.getLocalPort(),
              "ecom.keycloak.realm=ecom",
              "ecom.keycloak.client-id=backend",
              "ecom.keycloak.client-secret=local-only",
              "ecom.keycloak.read-timeout=300ms")
          .run(
              context -> {
                var started = System.nanoTime();
                assertThatThrownBy(() -> context.getBean(KeycloakAdmin.class).rotateSecret("c-1"))
                    .isInstanceOfSatisfying(
                        GeneralException.class,
                        e ->
                            assertThat(e.getStatus())
                                .isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
                assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .isLessThan(Duration.ofSeconds(5));
              });
    }
  }
}
