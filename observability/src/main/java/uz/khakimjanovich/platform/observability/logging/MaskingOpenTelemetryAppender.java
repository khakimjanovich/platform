package uz.khakimjanovich.platform.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

public class MaskingOpenTelemetryAppender extends OpenTelemetryAppender {

  @Override
  protected void append(ILoggingEvent event) {
    super.append(LogMasking.masked(event));
  }
}
