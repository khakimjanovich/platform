package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Sends every log line over OTLP with its trace, beside the console. The console stays Spring
 * Boot's own: readable text, or ECS JSON where logging.structured.format.console=ecs is set.
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
}
