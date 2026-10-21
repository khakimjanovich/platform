package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Keycloak without a realm or a client stops startup, naming the property. */
class KeycloakPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "ecom.keycloak.url=http://localhost:8180",
              "ecom.keycloak.client-id=backend",
              "ecom.keycloak.client-secret=local-only");

  @Test
  void aCompleteConfigurationStarts() {
    runner
        .withPropertyValues("ecom.keycloak.realm=ecom")
        .run(context -> assertThat(context).hasSingleBean(KeycloakAdmin.class));
  }

  @Test
  void aMissingRealmStopsStartup() {
    runner.run(
        context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("ecom.keycloak")
                .hasMessageContaining("realm"));
  }
}
