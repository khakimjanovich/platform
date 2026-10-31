package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

/**
 * Signing keys are where the app says, or not at all: a path without the client id would give every
 * client one key, and a path without its field or cache time stops startup.
 */
class VaultPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(VaultAutoConfiguration.class))
          .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

  @Test
  void withoutAPathThereAreNoSigningKeys() {
    runner.run(context -> assertThat(context).doesNotHaveBean(SigningKeys.class));
  }

  @Test
  void theAppsPathFieldAndCacheTimeStart() {
    runner
        .withPropertyValues(
            "platform.vault.signing-keys.path=clients/%s",
            "platform.vault.signing-keys.field=key", "platform.vault.signing-keys.cache-ttl=30s")
        .run(context -> assertThat(context).hasSingleBean(SigningKeys.class));
  }

  @Test
  void aPathWithoutItsFieldStopsStartup() {
    runner
        .withPropertyValues(
            "platform.vault.signing-keys.path=clients/%s",
            "platform.vault.signing-keys.cache-ttl=30s")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(VaultConstants.SIGNING_KEYS_FIELD_MISSING));
  }

  @Test
  void aPathWithoutItsCacheTimeStopsStartup() {
    runner
        .withPropertyValues(
            "platform.vault.signing-keys.path=clients/%s", "platform.vault.signing-keys.field=key")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(VaultConstants.SIGNING_KEYS_CACHE_TTL_MISSING));
  }

  @Test
  void aPathWithoutTheClientIdStopsStartup() {
    runner
        .withPropertyValues(
            "platform.vault.signing-keys.path=clients/",
            "platform.vault.signing-keys.field=key",
            "platform.vault.signing-keys.cache-ttl=30s")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("path")
                    .hasMessageContaining("must contain %s"));
  }
}
