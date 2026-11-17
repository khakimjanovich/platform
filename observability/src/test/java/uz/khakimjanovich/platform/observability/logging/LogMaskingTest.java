package uz.khakimjanovich.platform.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  void resetLoggingMasking() {
    LogMasking.install(
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory(),
        Masking.defaults());
  }

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
  void otlpEventPreservesMaskedThrowableProxyWithStructuredAttributes() {
    var original = new IllegalStateException("card pan=4100301234567891, client_secret=t0p");
    var event = new LoggingEvent();
    event.setMessage("failed");
    event.setThrowableProxy(new ThrowableProxy(original));
    LogMasking.install(
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory(),
        new Masking(MaskingRules.of(List.of("pan", "client_secret"))));

    var masked = LogMasking.masked(event);
    assertThat(masked.getFormattedMessage()).isEqualTo("failed");
    assertThat(masked.getArgumentArray()).isNull();

    var proxy = masked.getThrowableProxy();
    assertThat(proxy).isNotNull().isInstanceOf(ThrowableProxy.class);
    assertThat(proxy.getClassName()).isEqualTo("java.lang.IllegalStateException");
    assertThat(proxy.getMessage())
        .isEqualTo("card pan=410030......7891, client_secret=...")
        .doesNotContain("4100301234567891", "t0p");

    var throwable = ((ThrowableProxy) proxy).getThrowable();
    assertThat(throwable).isNotNull().isInstanceOf(IllegalStateException.class);
    assertThat(throwable.getMessage())
        .isEqualTo("card pan=410030......7891, client_secret=...")
        .doesNotContain("4100301234567891", "t0p");
    assertThat(throwable.getStackTrace()).isEqualTo(original.getStackTrace());
  }

  @Test
  void otlpEventRecursivelyMasksNestedCausesAndSuppressedExceptions() {
    var cause =
        new IllegalArgumentException("nested cause pan=5600301234567892, client_secret=c4us3");
    var suppressed = new RuntimeException("suppressed pan=6600301234567893, client_secret=h1dd3n");
    var root = new IllegalStateException("root pan=4100301234567891, client_secret=t0p", cause);
    root.addSuppressed(suppressed);

    var event = new LoggingEvent();
    event.setMessage("payment error");
    event.setThrowableProxy(new ThrowableProxy(root));
    LogMasking.install(
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory(),
        new Masking(MaskingRules.of(List.of("pan", "client_secret"))));

    var masked = LogMasking.masked(event);
    var proxy = masked.getThrowableProxy();
    assertThat(proxy).isNotNull();
    assertThat(proxy.getMessage()).contains("pan=410030......7891", "client_secret=...");

    var causeProxy = proxy.getCause();
    assertThat(causeProxy).isNotNull().isInstanceOf(ThrowableProxy.class);
    assertThat(causeProxy.getClassName()).isEqualTo("java.lang.IllegalArgumentException");
    assertThat(causeProxy.getMessage())
        .contains("pan=560030......7892", "client_secret=...")
        .doesNotContain("5600301234567892", "c4us3");

    var suppressedProxies = proxy.getSuppressed();
    assertThat(suppressedProxies).hasSize(1);
    assertThat(suppressedProxies[0].getClassName()).isEqualTo("java.lang.RuntimeException");
    assertThat(suppressedProxies[0].getMessage())
        .contains("pan=660030......7893", "client_secret=...")
        .doesNotContain("6600301234567893", "h1dd3n");

    var sanitizedThrowable = ((ThrowableProxy) proxy).getThrowable();
    assertThat(sanitizedThrowable.getCause())
        .isNotNull()
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sanitizedThrowable.getCause().getMessage())
        .contains("pan=560030......7892", "client_secret=...");
    assertThat(sanitizedThrowable.getSuppressed()).hasSize(1);
    assertThat(sanitizedThrowable.getSuppressed()[0].getMessage())
        .contains("pan=660030......7893", "client_secret=...");
  }

  @Test
  void otlpEventWithoutThrowableLeavesThrowableProxyNull() {
    var event = new LoggingEvent();
    event.setMessage("no exception here pan=8600301234567890");
    event.setThrowableProxy(null);
    LogMasking.install(
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory(),
        new Masking(MaskingRules.of(List.of("pan"))));

    var masked = LogMasking.masked(event);
    assertThat(masked.getThrowableProxy()).isNull();
    assertThat(masked.getFormattedMessage()).isEqualTo("no exception here pan=860030......7890");
  }
}
