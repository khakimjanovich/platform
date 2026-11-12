package uz.khakimjanovich.platform.vault;

import org.junit.jupiter.api.Test;
import uz.khakimjanovich.platform.error.ErrorDefinitions;

/** Every Transit error has a definition in every language, and none is left over. */
class VaultExceptionStatusTest {

  @Test
  void everyErrorIsDefinedInEveryLanguage() {
    ErrorDefinitions.assertComplete(
        "uz.khakimjanovich.platform.vault", "classpath*:properties/vault-exception.yaml");
  }
}
