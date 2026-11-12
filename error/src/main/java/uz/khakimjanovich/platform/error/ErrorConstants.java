package uz.khakimjanovich.platform.error;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorConstants {

  public static final String PROPERTIES_PREFIX = "platform.error";

  public static final String CODE_PROPERTY = "code";

  public static final String LOG_UNEXPECTED_ERROR = "Unexpected error";
  public static final String LOG_INVALID = "invalid fields ";
  public static final String LOG_NO_DEFINITION = "No definition for error {}; answering 500";

  public static final String LOG_PROBLEM = "✗ %d %s | %s | %s";

  public static final String REQUEST_ID_PROPERTY = "requestId";

  public static final String TRACE_ID_MDC = "traceId";

  public static final ErrorStarterProperties.ErrorDefinition LAST_RESORT =
      new ErrorStarterProperties.ErrorDefinition(
          500, "internal-error", true, "Something went wrong.", null, null);

  public static final String ERRORS_PROPERTY = "errors";

  public static final String SUB_ERRORS_PROPERTY = "subErrors";

  public static final String FIELD = "field";
  public static final String MESSAGE = "message";

  /* ------- security ------- */
  public static final String BEARER_CHALLENGE = "Bearer";

  public static final String BOOT_WEB_MVC =
      "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration";

  public static final String ACCESS_DENIED =
      "org.springframework.security.access.AccessDeniedException";

  public static final String EXCEPTION_RESOLVER = "handlerExceptionResolver";
}
