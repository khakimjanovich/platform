package uz.heeypay.ecom.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

/** A card number or a secret in an ordinary log line never reaches the console as written. */
@ExtendWith(OutputCaptureExtension.class)
class LogMaskingTest {

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(ObservabilityAutoConfiguration.class)
  static class App {}

  private static void logInAnApp(String... arguments) {
    try (var context =
        new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run(arguments)) {
      LoggerFactory.getLogger(LogMaskingTest.class)
          .info("paid with 8600301234567890, client_secret=s3cr3t, EMBOS_NAME=AZIZ");
    }
  }

  @Test
  void theReadableConsoleMasksTheMessage(CapturedOutput output) {
    logInAnApp();
    assertThat(output)
        .contains("paid with 860030******7890, client_secret=***")
        .doesNotContain("8600301234567890", "s3cr3t");
  }

  @Test
  void ecsJsonMasksTheMessage(CapturedOutput output) {
    logInAnApp("--logging.structured.format.console=ecs");
    assertThat(output)
        .contains("\"message\":\"paid with 860030******7890, client_secret=***")
        .doesNotContain("8600301234567890", "s3cr3t");
  }

  @Test
  void aServiceAddsItsOwnNames(CapturedOutput output) {
    logInAnApp("--ecom.observability.masking.additional-secret-fields=EMBOS_NAME");
    assertThat(output).contains("EMBOS_NAME=***").doesNotContain("AZIZ");
  }
}
