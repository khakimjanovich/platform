package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.heeypay.ecom.masking.Masking;
import uz.heeypay.ecom.masking.MaskingRules;

/**
 * Sends every log line over OTLP with its trace, beside the console (Spring Boot's own: readable
 * text, or ECS JSON where logging.structured.format.console=ecs is set), and logs every outgoing
 * call of every client built from Boot's RestClient.Builder. Every log line, on every sink, is
 * masked with the default names plus the service's own ({@code ecom.observability.masking.*}).
 * Every web response carries its trace id as {@code X-Request-Id}.
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Masking logMasking(ObservabilityProperties properties) {
    var extra = properties.masking();
    return new Masking(
        MaskingRules.defaults()
            .with(
                extra.additionalSecretFields(),
                extra.additionalTokenFields(),
                extra.additionalCardFields()));
  }

  /** The console prints masked messages, and OTLP gets every line through a masking appender. */
  @Bean
  InitializingBean maskedLogs(Masking logMasking, ObjectProvider<OpenTelemetry> openTelemetry) {
    return () -> {
      var context = (LoggerContext) LoggerFactory.getILoggerFactory();
      LogMasking.install(context, logMasking);
      openTelemetry.ifAvailable(
          otel -> {
            var appender = new MaskingOpenTelemetryAppender();
            appender.setContext(context);
            appender.setName(ObservabilityConstants.OTEL_APPENDER);
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
            OpenTelemetryAppender.install(otel);
          });
    };
  }

  /** For a client not built from Boot's RestClient.Builder (a SOAP one) to add itself. */
  @Bean
  @ConditionalOnMissingBean
  HttpClientLogging httpClientLogging(Masking logMasking) {
    return new HttpClientLogging(logMasking);
  }

  @Bean
  RestClientCustomizer httpClientLoggingCustomizer(HttpClientLogging httpClientLogging) {
    return builder -> builder.requestInterceptor(httpClientLogging);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(Tracer.class)
  static class RequestIds {

    @Bean
    @ConditionalOnMissingBean
    RequestIdFilter requestIdFilter(ObjectProvider<Tracer> tracer) {
      return new RequestIdFilter(tracer);
    }
  }

  /**
   * With the actuator: every health indicator as a gauge, for alerts on a dependency going DOWN.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(HealthContributorRegistry.class)
  static class Health {

    @Bean
    @ConditionalOnMissingBean
    HealthMetrics healthMetrics(
        ObjectProvider<HealthContributorRegistry> registry, ObjectProvider<MeterRegistry> meters) {
      return new HealthMetrics(registry, meters);
    }
  }
}
