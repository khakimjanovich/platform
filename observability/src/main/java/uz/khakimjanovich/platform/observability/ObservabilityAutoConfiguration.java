package uz.khakimjanovich.platform.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;
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
import uz.khakimjanovich.platform.masking.Masking;
import uz.khakimjanovich.platform.masking.MaskingRules;

@AutoConfiguration
@EnableConfigurationProperties({ObservabilityProperties.class, LoggingMaskingProperties.class})
public class ObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Masking logMasking(
      LoggingMaskingProperties loggingProperties, ObservabilityProperties observabilityProperties) {
    if (!loggingProperties.enabled()) {
      return Masking.disabled();
    }
    var rules = MaskingRules.defaults();
    if (!loggingProperties.cardPan()) {
      rules = rules.withoutCards();
    }
    if (!loggingProperties.cvv()) {
      rules = rules.withoutCvv();
    }
    if (!loggingProperties.bearerToken()) {
      rules = rules.withoutTokens();
    }
    if (!loggingProperties.phoneNumber()) {
      rules = rules.withoutPhone();
    }
    if (!loggingProperties.pinfl()) {
      rules = rules.withoutPinfl();
    }

    var legacy = observabilityProperties.masking();
    var secretFields = new ArrayList<>(loggingProperties.additionalSecretFields());
    secretFields.addAll(legacy.additionalSecretFields());

    var tokenFields = new ArrayList<>(loggingProperties.additionalTokenFields());
    tokenFields.addAll(legacy.additionalTokenFields());

    var cardFields = new ArrayList<>(loggingProperties.additionalCardFields());
    cardFields.addAll(legacy.additionalCardFields());

    var secretsBeside = new HashMap<>(loggingProperties.additionalSecretsBeside());
    legacy
        .additionalSecretsBeside()
        .forEach(
            (key, context) ->
                secretsBeside.merge(
                    key,
                    context,
                    (c1, c2) -> Stream.concat(c1.stream(), c2.stream()).distinct().toList()));

    return new Masking(rules.with(secretFields, tokenFields, cardFields, secretsBeside));
  }

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
