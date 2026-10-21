package uz.heeypay.ecom.error;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorConstants {

  public static final String PROPERTIES_PREFIX = "exception.starter";

  /** Extra property on every problem detail: the stable, machine-readable error code. */
  public static final String CODE_PROPERTY = "code";

  public static final String LOG_UNEXPECTED_ERROR = "Unexpected error";
  public static final String LOG_NO_DEFINITION = "No definition for error {}; answering 500";

  /** Every error response, once: status, code, the request, and why. */
  public static final String LOG_PROBLEM = "✗ %d %s | %s | %s";

  /** Used only when a service overrode the shared definitions away. */
  public static final ErrorStarterProperties.ErrorDefinition LAST_RESORT =
      new ErrorStarterProperties.ErrorDefinition(
          500, "internal-error", true, "Something went wrong.", null, null);

  /** Extra property on a validation problem: [{ field, message }]. */
  public static final String ERRORS_PROPERTY = "errors";

  public static final String SUB_ERRORS_PROPERTY = "subErrors";

  public static final String FIELD = "field";
  public static final String MESSAGE = "message";

  /* ------- security ------- */
  public static final String BEARER_CHALLENGE = "Bearer";

  /** Spring MVC's composite exception resolver bean. */
  public static final String EXCEPTION_RESOLVER = "handlerExceptionResolver";
}
