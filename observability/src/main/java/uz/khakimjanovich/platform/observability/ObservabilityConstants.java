package uz.khakimjanovich.platform.observability;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ObservabilityConstants {

  /** The Logback appender that sends every line over OTLP. */
  public static final String OTEL_APPENDER = "OTEL";

  public static final String HEALTH_METRIC = "platform.health.up";
  public static final String COMPONENT_TAG = "component";
  public static final String HEALTH_METRIC_DESCRIPTION = "1 while the component's health is UP";
}
