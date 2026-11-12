package uz.khakimjanovich.platform.vault;

import org.junit.jupiter.api.Test;
import uz.khakimjanovich.platform.error.ErrorDefinitions;

class VaultExceptionStatusTest {

  @Test
  void everyErrorIsDefinedInEveryLanguage() {
    ErrorDefinitions.assertComplete(
        "uz.khakimjanovich.platform.vault", "classpath*:properties/vault-exception.yaml");
  }
}
