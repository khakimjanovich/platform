package uz.khakimjanovich.platform.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * No raw HTTP failure escapes: a conflict, refused input, or Keycloak unavailable with its own
 * message; a refused token is replaced once.
 */
class KeycloakAdminTest {

  private final RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final KeycloakAdmin admin = new KeycloakAdmin(builder.build());

  @Test
  void anExistingUserIsAConflict() {
    server.expect(requestTo("http://keycloak/users")).andRespond(withStatus(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> admin.createUser("aziz", "aziz@example.com", "pw", false, List.of()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_CONFLICT);
              assertThat(e.getParams()).containsExactly("aziz");
            });
  }

  @Test
  void aCreatedUserCarriesTheRequiredActionsAsked() {
    server
        .expect(requestTo("http://keycloak/users"))
        .andExpect(
            content().json("{\"requiredActions\":[\"CONFIGURE_TOTP\"]}", JsonCompareMode.LENIENT))
        .andRespond(
            withStatus(HttpStatus.CREATED).location(URI.create("http://keycloak/users/user-1")));
    assertThat(
            admin.createUser(
                "aziz", "aziz@example.com", "pw", false, List.of(KeycloakConstants.CONFIGURE_TOTP)))
        .isEqualTo("user-1");
    server.verify();
  }

  /** The user and the e-mail both carry the password and every action asked. */
  @Test
  void anInviteSetsAndEmailsAPasswordAndTheRequiredActionsAsked() {
    var actions = "[\"UPDATE_PASSWORD\",\"CONFIGURE_TOTP\"]";
    server
        .expect(requestTo("http://keycloak/users"))
        .andExpect(content().json("{\"requiredActions\":" + actions + "}", JsonCompareMode.LENIENT))
        .andRespond(
            withStatus(HttpStatus.CREATED).location(URI.create("http://keycloak/users/user-1")));
    server
        .expect(requestTo("http://keycloak/users/user-1/execute-actions-email"))
        .andExpect(content().json(actions, JsonCompareMode.STRICT))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    assertThat(
            admin.inviteUser(
                "aziz@example.com", "Aziza", List.of(KeycloakConstants.CONFIGURE_TOTP)))
        .isEqualTo("user-1");
    server.verify();
  }

  @Test
  void anInviteWhoseEmailFailsRemovesTheUserAgain() {
    server
        .expect(requestTo("http://keycloak/users"))
        .andRespond(
            withStatus(HttpStatus.CREATED).location(URI.create("http://keycloak/users/user-1")));
    server
        .expect(requestTo("http://keycloak/users/user-1/execute-actions-email"))
        .andExpect(content().json("[\"UPDATE_PASSWORD\"]", JsonCompareMode.STRICT))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    server
        .expect(requestTo("http://keycloak/users/user-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    assertThatThrownBy(() -> admin.inviteUser("aziz@example.com", "Aziza", List.of()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
    server.verify();
  }

  @Test
  void anInviteWithAnUnknownRoleRemovesTheUserAgain() {
    server
        .expect(requestTo("http://keycloak/users"))
        .andRespond(
            withStatus(HttpStatus.CREATED).location(URI.create("http://keycloak/users/user-1")));
    server
        .expect(requestTo("http://keycloak/roles/no-such-role"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    server
        .expect(requestTo("http://keycloak/users/user-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    assertThatThrownBy(
            () -> admin.inviteUser("aziz@example.com", "Aziza", List.of(), "no-such-role"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_REJECTED));
    server.verify();
  }

  /** Only the field that changes is sent: Keycloak leaves the rest of the user alone. */
  @Test
  void disablingSendsOnlyEnabledThenEndsTheSessions() {
    server
        .expect(requestTo("http://keycloak/users/user-1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"enabled\":false}", JsonCompareMode.STRICT))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    server
        .expect(requestTo("http://keycloak/users/user-1/logout"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    admin.disableUser("user-1");
    server.verify();
  }

  @Test
  void aCreateAnsweredWithoutALocationIsUnavailable() {
    server.expect(requestTo("http://keycloak/users")).andRespond(withStatus(HttpStatus.CREATED));
    assertThatThrownBy(() -> admin.createUser("aziz", "aziz@example.com", "pw", false, List.of()))
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
            e -> {
              assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE);
              assertThat(e.getSubErrors().toString()).as("no URL").doesNotContain("http");
            });
  }

  @Test
  void refusedInputIsRejectedWithKeycloaksMessage() {
    server
        .expect(requestTo("http://keycloak/users"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorMessage\":\"invalidPasswordMinLengthMessage\"}"));
    assertThatThrownBy(() -> admin.createUser("aziz", "aziz@example.com", "pw", false, List.of()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(KeycloakExceptionStatus.KEYCLOAK_REJECTED);
              assertThat(e.getSubErrors())
                  .containsExactly(
                      new GeneralException.SubError("400", "invalidPasswordMinLengthMessage"));
            });
  }

  /** Keycloak restarted: the stored token is refused once, so a new one is fetched and used. */
  @Test
  void aRefusedTokenIsDroppedAndTheCallTriedOnceMore() throws IOException {
    var tokens = new AtomicInteger();
    var users = new AtomicInteger();
    var keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    keycloak.createContext(
        "/realms/demo/protocol/openid-connect/token",
        exchange -> {
          var token =
              "{\"access_token\":\"t"
                  + tokens.incrementAndGet()
                  + "\","
                  + "\"token_type\":\"Bearer\",\"expires_in\":300}";
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, token.length());
          exchange.getResponseBody().write(token.getBytes());
          exchange.close();
        });
    keycloak.createContext(
        "/admin/realms/demo/users",
        exchange -> {
          if (users.incrementAndGet() == 1) {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            exchange.sendResponseHeaders(401, -1);
          } else {
            exchange.getResponseHeaders().add("Location", "http://keycloak/users/u-1");
            exchange.sendResponseHeaders(201, -1);
          }
          exchange.close();
        });
    keycloak.start();
    try {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "platform.keycloak.url=http://localhost:" + keycloak.getAddress().getPort(),
              "platform.keycloak.realm=demo",
              "platform.keycloak.client-id=backend",
              "platform.keycloak.client-secret=local-only")
          .run(
              context -> {
                var id =
                    context
                        .getBean(KeycloakAdmin.class)
                        .createUser("aziz", "aziz@example.com", "pw", false, List.of());
                assertThat(id).isEqualTo("u-1");
                assertThat(tokens).hasValue(2);
                assertThat(users).hasValue(2);
              });
    } finally {
      keycloak.stop(0);
    }
  }

  /** A Keycloak that accepts the connection and never answers: the read timeout ends the wait. */
  @Test
  void theReadTimeoutIsApplied() throws IOException {
    try (var silent = new ServerSocket(0)) {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "platform.keycloak.url=http://localhost:" + silent.getLocalPort(),
              "platform.keycloak.realm=demo",
              "platform.keycloak.client-id=backend",
              "platform.keycloak.client-secret=local-only",
              "platform.keycloak.read-timeout=300ms")
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
