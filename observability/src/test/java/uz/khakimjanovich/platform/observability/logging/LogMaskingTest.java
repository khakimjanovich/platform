package uz.khakimjanovich.platform.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import uz.khakimjanovich.platform.masking.Masking;
import uz.khakimjanovich.platform.masking.MaskingRules;
import uz.khakimjanovich.platform.observability.ObservabilityAutoConfiguration;

@ExtendWith(OutputCaptureExtension.class)
class LogMaskingTest {

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(ObservabilityAutoConfiguration.class)
  static class App {}

  private static void logInAnApp(String... arguments) {
    try (var _ =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            // No setting has a default; no collector runs here, and nothing is traced.
            .properties(
                "TRACING_SAMPLING=0.0",
                "OTLP_TRACES_ENDPOINT=http://otlp.not-started.invalid/v1/traces",
                "OTLP_METRICS_ENDPOINT=http://otlp.not-started.invalid/v1/metrics",
                "OTLP_LOGS_ENDPOINT=http://otlp.not-started.invalid/v1/logs")
            .run(arguments)) {
      LoggerFactory.getLogger(LogMaskingTest.class)
          .info("paid with pan=8600301234567890, client_secret=s3cr3t, EMBOS_NAME=AZIZ");
      LoggerFactory.getLogger(LogMaskingTest.class)
          .error(
              "failed",
              new IllegalStateException("card pan=4100301234567891 declined, client_secret=t0p"));
    }
  }

  @Test
  void unconfiguredLoggingMaskingLeavesLogsCompletelyUnmasked(CapturedOutput output) {
    logInAnApp();
    assertThat(output).contains("pan=8600301234567890", "client_secret=s3cr3t", "EMBOS_NAME=AZIZ");
  }

  @Test
  void configuredFieldsMaskWithFirst6DotsLast4AndShortDots(CapturedOutput output) {
    logInAnApp("--platform.logging.masking.fields=pan,client_secret");
    assertThat(output)
        .contains("pan=860030......7890", "client_secret=......", "EMBOS_NAME=AZIZ")
        .doesNotContain("8600301234567890", "s3cr3t");
  }

  @Test
  void ecsJsonMasksConfiguredFields(CapturedOutput output) {
    logInAnApp(
        "--logging.structured.format.console=ecs",
        "--platform.logging.masking.fields=pan,client_secret");
    assertThat(output)
        .contains("\"message\":\"paid with pan=860030......7890, client_secret=......")
        .doesNotContain("8600301234567890", "s3cr3t");
  }

  @Test
  void stackTraceMasksConfiguredFields(CapturedOutput output) {
    logInAnApp("--platform.logging.masking.fields=pan,client_secret");
    assertThat(output)
        .contains("card pan=410030......7891 declined, client_secret=...")
        .contains(
            "IllegalStateException",
            "at uz.khakimjanovich.platform.observability.logging.LogMaskingTest")
        .doesNotContain("4100301234567891", "t0p");
  }

  @Test
  void otlpGetsTheStackTraceMaskedInTheMessage() {
    var event = new LoggingEvent();
    event.setMessage("failed");
    event.setThrowableProxy(
        new ThrowableProxy(
            new IllegalStateException("card pan=4100301234567891, client_secret=t0p")));
    LogMasking.install(
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory(),
        new Masking(MaskingRules.of(List.of("pan", "client_secret"))));

    var masked = LogMasking.masked(event);
    assertThat(masked.getThrowableProxy()).isNull();
    assertThat(masked.getFormattedMessage())
        .contains("failed", "IllegalStateException", "pan=410030......7891", "client_secret=...")
        .doesNotContain("4100301234567891", "t0p");
  }
}
