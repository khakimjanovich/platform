package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import uz.heeypay.ecom.error.GeneralException;

/** Keycloak going away mid-life is KEYCLOAK_UNAVAILABLE, never a raw client exception. */
class KeycloakStoppedIT {

  @Test
  void aStoppedKeycloakIsUnavailable() {
    var keycloak =
        new GenericContainer<>(ResourceBundle.getBundle("images").getString("keycloak"))
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("test-realm.json"),
                "/opt/keycloak/data/import/test-realm.json")
            .withExposedPorts(8080)
            .waitingFor(
                Wait.forHttp("/realms/test")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    keycloak.start();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
        .withPropertyValues(
            "ecom.keycloak.url=http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080),
            "ecom.keycloak.realm=test",
            "ecom.keycloak.client-id=backend",
            "ecom.keycloak.client-secret=test-secret")
        .run(
            context -> {
              var admin = context.getBean(KeycloakAdmin.class);
              var issued = admin.createConfidentialClient("stopped-it");
              keycloak.stop();
              assertThatThrownBy(() -> admin.rotateSecret(issued.id()))
                  .isInstanceOfSatisfying(
                      GeneralException.class,
                      e ->
                          assertThat(e.getStatus())
                              .isEqualTo(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE));
            });
  }
}
