package uz.khakimjanovich.platform.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KeycloakPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "platform.keycloak.url=http://localhost:8180", "platform.keycloak.client-id=backend");

  @Test
  void aCompleteConfigurationStarts() {
    runner
        .withPropertyValues(
            "platform.keycloak.realm=demo", "platform.keycloak.client-secret=local-only")
        .run(context -> assertThat(context).hasSingleBean(KeycloakAdmin.class));
  }

  @Test
  void aMissingRealmStopsStartup() {
    runner
        .withPropertyValues("platform.keycloak.client-secret=local-only")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.keycloak")
                    .hasMessageContaining("realm"));
  }

  @Test
  void aMissingSecretStopsStartup() {
    runner
        .withPropertyValues("platform.keycloak.realm=demo")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.keycloak")
                    .hasMessageContaining("clientSecret"));
  }
}
