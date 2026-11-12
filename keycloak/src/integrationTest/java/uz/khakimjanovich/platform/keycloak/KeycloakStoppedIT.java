package uz.khakimjanovich.platform.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import uz.khakimjanovich.platform.error.GeneralException;

class KeycloakStoppedIT {

  @Test
  void aStoppedKeycloakIsUnavailable() {
    var keycloak =
        new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("demo-realm.json"),
                "/opt/keycloak/data/import/demo-realm.json")
            .withExposedPorts(8080)
            .waitingFor(
                Wait.forHttp("/realms/demo")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    keycloak.start();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
        .withPropertyValues(
            "platform.keycloak.url=http://"
                + keycloak.getHost()
                + ":"
                + keycloak.getMappedPort(8080),
            "platform.keycloak.realm=demo",
            "platform.keycloak.client-id=demo-backend",
            "platform.keycloak.client-secret=local-only")
        .run(
            context -> {
              var admin = context.getBean(KeycloakAdmin.class);
              var issued = admin.createConfidentialClient("stopped-it");
              var health = context.getBean("keycloakHealthIndicator", HealthIndicator.class);
              assertThat(health.health().getStatus()).isEqualTo(Status.UP);
              keycloak.stop();
              assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
              assertThatThrownBy(() -> admin.rotateSecret(issued.id()))
                  .isInstanceOfSatisfying(
                      GeneralException.class,
                      e ->
                          assertThat(e.getStatus())
                              .isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
            });
  }
}
