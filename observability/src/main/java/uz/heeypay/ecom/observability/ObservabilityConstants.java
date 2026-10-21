package uz.heeypay.ecom.observability;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ObservabilityConstants {

  /** The Logback appender that sends every line over OTLP. */
  public static final String OTEL_APPENDER = "OTEL";
}
