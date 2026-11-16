package uz.khakimjanovich.platform.breaker;

import org.junit.jupiter.api.Test;
import uz.khakimjanovich.platform.error.ErrorDefinitions;

class BreakerExceptionStatusTest {

  @Test
  void everyErrorIsDefinedInEveryLanguage() {
    ErrorDefinitions.assertComplete(
        "uz.khakimjanovich.platform.breaker", "classpath*:properties/breaker-exception.yaml");
  }
}
