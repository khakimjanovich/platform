package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Keycloak without a realm, a client or its secret stops startup, naming the property. */
class KeycloakPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class))
          .withPropertyValues(
              "ecom.keycloak.url=http://localhost:8180", "ecom.keycloak.client-id=backend");

  @Test
  void aCompleteConfigurationStarts() {
    runner
        .withPropertyValues("ecom.keycloak.realm=ecom", "ecom.keycloak.client-secret=local-only")
        .run(context -> assertThat(context).hasSingleBean(KeycloakAdmin.class));
  }

  @Test
  void aMissingRealmStopsStartup() {
    runner
        .withPropertyValues("ecom.keycloak.client-secret=local-only")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("ecom.keycloak")
                    .hasMessageContaining("realm"));
  }

  @Test
  void aMissingSecretStopsStartup() {
    runner
        .withPropertyValues("ecom.keycloak.realm=ecom")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("ecom.keycloak")
                    .hasMessageContaining("clientSecret"));
  }
}
