package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/** OpenTelemetry's Logback appender, handed every event with its message already masked. */
class MaskingOpenTelemetryAppender extends OpenTelemetryAppender {

  @Override
  protected void append(ILoggingEvent event) {
    super.append(LogMasking.masked(event));
  }
}
