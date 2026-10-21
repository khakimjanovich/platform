package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import uz.heeypay.ecom.masking.Masking;

/**
 * Sends every log line over OTLP with its trace, beside the console (Spring Boot's own: readable
 * text, or ECS JSON where logging.structured.format.console=ecs is set), and logs every outgoing
 * call of every client built from Boot's RestClient.Builder, with masked bodies.
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

  @Bean
  InitializingBean openTelemetryLogExport(ObjectProvider<OpenTelemetry> openTelemetry) {
    return () ->
        openTelemetry.ifAvailable(
            otel -> {
              var context = (LoggerContext) LoggerFactory.getILoggerFactory();
              var appender = new OpenTelemetryAppender();
              appender.setContext(context);
              appender.setName("OTEL");
              appender.start();
              context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
              OpenTelemetryAppender.install(otel);
            });
  }

  /** For a client not built from Boot's RestClient.Builder (a SOAP one) to add itself. */
  @Bean
  @ConditionalOnMissingBean
  HttpClientLogging httpClientLogging() {
    return new HttpClientLogging(Masking.defaults());
  }

  @Bean
  RestClientCustomizer httpClientLoggingCustomizer(HttpClientLogging httpClientLogging) {
    return builder -> builder.requestInterceptor(httpClientLogging);
  }
}
