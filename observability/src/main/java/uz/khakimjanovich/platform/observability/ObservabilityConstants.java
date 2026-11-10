package uz.khakimjanovich.platform.observability;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ObservabilityConstants {

  /** The Logback appender that sends every line over OTLP. */
  public static final String OTEL_APPENDER = "OTEL";

  public static final String HEALTH_METRIC = "platform.health.up";
  public static final String COMPONENT_TAG = "component";
  public static final String HEALTH_METRIC_DESCRIPTION = "1 while the component's health is UP";

  static final int MAX_LOGGED_BYTES = 4_000;

  /** The request attribute that keeps both bodies of a call out of the log. */
  public static final String NO_BODIES = "platform.http-client-logging.no-bodies";

  static final String NOT_LOGGED = "<not logged>";

  /** Logback's names for the message in a pattern: %m, %msg, %message. */
  static final List<String> MESSAGE_WORDS = List.of("m", "msg", "message");

  /** Logback's and Boot's names for the stack trace in a pattern (Boot's default is %wEx). */
  static final List<String> THROWABLE_WORDS =
      List.of("ex", "exception", "throwable", "xEx", "xException", "xThrowable", "wex", "wEx");

  /** ECS members that carry text a program wrote. */
  static final List<String> JSON_TEXT = List.of("message", "error.message", "error.stack_trace");

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
}
