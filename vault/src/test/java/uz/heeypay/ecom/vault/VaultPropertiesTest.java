package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

/** A signing-key path without the client id would give every client one key: it stops startup. */
class VaultPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(VaultAutoConfiguration.class))
          .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

  @Test
  void theDefaultPathStarts() {
    runner.run(context -> assertThat(context).hasSingleBean(SigningKeys.class));
  }

  @Test
  void aPathWithoutTheClientIdStopsStartup() {
    runner
        .withPropertyValues("ecom.vault.signing-keys.path=merchants/")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("signingKeys.path")
                    .hasMessageContaining("must contain %s"));
  }
}
