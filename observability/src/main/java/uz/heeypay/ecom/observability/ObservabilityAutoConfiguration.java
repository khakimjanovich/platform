package uz.heeypay.ecom.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Hands Boot's OpenTelemetry to the Logback appender in logback-spring.xml, which then sends every
 * log line over OTLP with its trace.
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

  @Bean
  InitializingBean openTelemetryAppenderInstaller(ObjectProvider<OpenTelemetry> openTelemetry) {
    return () -> openTelemetry.ifAvailable(OpenTelemetryAppender::install);
  }
}
