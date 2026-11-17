package uz.khakimjanovich.platform.observability;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.khakimjanovich.platform.breaker.Breaker;
import uz.khakimjanovich.platform.masking.Masking;
import uz.khakimjanovich.platform.masking.MaskingRules;
import uz.khakimjanovich.platform.observability.breaker.BreakerMetrics;
import uz.khakimjanovich.platform.observability.health.HealthMetrics;
import uz.khakimjanovich.platform.observability.http.HttpClientLogging;
import uz.khakimjanovich.platform.observability.http.HttpLoggingProperties;
import uz.khakimjanovich.platform.observability.http.HttpServerLoggingFilter;
import uz.khakimjanovich.platform.observability.http.RequestIdFilter;
import uz.khakimjanovich.platform.observability.logging.LogMasking;
import uz.khakimjanovich.platform.observability.logging.LoggingMaskingProperties;
import uz.khakimjanovich.platform.observability.logging.MaskingOpenTelemetryAppender;

@AutoConfiguration
@EnableConfigurationProperties({LoggingMaskingProperties.class, HttpLoggingProperties.class})
public class ObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Masking logMasking(LoggingMaskingProperties loggingProperties) {
    var fields = loggingProperties.fields();
    if (fields == null || fields.isEmpty()) {
      return Masking.empty();
    }
    var maskChar = loggingProperties.maskChar();
    return maskChar == null
        ? new Masking(MaskingRules.of(fields))
        : new Masking(MaskingRules.of(maskChar, fields));
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
  HttpClientLogging httpClientLogging(
      Masking logMasking, ObjectProvider<HttpLoggingProperties> httpLoggingProperties) {
    var props = httpLoggingProperties.getIfAvailable();
    return props == null
        ? new HttpClientLogging(logMasking)
        : new HttpClientLogging(logMasking, props);
  }

  @Bean
  RestClientCustomizer httpClientLoggingCustomizer(HttpClientLogging httpClientLogging) {
    return builder -> builder.requestInterceptor(httpClientLogging);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class WebLogging {

    @Bean
    @ConditionalOnProperty(name = "platform.logging.http.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    HttpServerLoggingFilter httpServerLoggingFilter(
        Masking logMasking, HttpLoggingProperties httpLoggingProperties) {
      return new HttpServerLoggingFilter(logMasking, httpLoggingProperties);
    }
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

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({Breaker.class, MeterRegistry.class})
  static class Breakers {

    @Bean
    @ConditionalOnMissingBean
    BreakerMetrics breakerMetrics(
        ObjectProvider<Breaker> breakers, ObjectProvider<MeterRegistry> meters) {
      return new BreakerMetrics(breakers, meters);
    }
  }
}
