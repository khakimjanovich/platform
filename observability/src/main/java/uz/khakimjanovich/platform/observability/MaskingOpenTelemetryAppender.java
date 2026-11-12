package uz.khakimjanovich.platform.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

class MaskingOpenTelemetryAppender extends OpenTelemetryAppender {

  @Override
  protected void append(ILoggingEvent event) {
    super.append(LogMasking.masked(event));
  }
}
