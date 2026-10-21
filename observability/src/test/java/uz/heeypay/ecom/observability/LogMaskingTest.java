package uz.heeypay.ecom.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
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
      LoggerFactory.getLogger(LogMaskingTest.class)
          .error(
              "failed",
              new IllegalStateException("card 4100301234567891 declined, client_secret=t0p"));
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
  void theReadableConsoleMasksTheStackTrace(CapturedOutput output) {
    logInAnApp();
    assertThat(output)
        .contains("card 410030******7891 declined, client_secret=***")
        .contains("IllegalStateException", "at uz.heeypay.ecom.observability.LogMaskingTest")
        .doesNotContain("4100301234567891", "t0p");
  }

  @Test
  void ecsJsonMasksTheStackTrace(CapturedOutput output) {
    logInAnApp("--logging.structured.format.console=ecs");
    assertThat(output)
        .contains("card 410030******7891 declined, client_secret=***")
        .contains("IllegalStateException", "LogMaskingTest")
        .doesNotContain("4100301234567891", "t0p");
  }

  @Test
  void otlpGetsTheStackTraceMaskedInTheMessage() {
    var event = new LoggingEvent();
    event.setMessage("failed");
    event.setThrowableProxy(
        new ThrowableProxy(new IllegalStateException("card 4100301234567891, client_secret=t0p")));
    var masked = LogMasking.masked(event);
    assertThat(masked.getThrowableProxy()).isNull();
    assertThat(masked.getFormattedMessage())
        .contains("failed", "IllegalStateException", "410030******7891", "client_secret=***")
        .doesNotContain("4100301234567891", "t0p");
  }

  @Test
  void aServiceAddsItsOwnNames(CapturedOutput output) {
    logInAnApp("--ecom.observability.masking.additional-secret-fields=EMBOS_NAME");
    assertThat(output).contains("EMBOS_NAME=***").doesNotContain("AZIZ");
  }
}
