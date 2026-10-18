package uz.heeypay.ecom.error;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorConstantUtils {

  /** Extra property on every problem detail: the stable, machine-readable error code. */
  public static final String CODE_PROPERTY = "code";

  public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
  public static final String INTERNAL_ERROR_DETAIL = "Something went wrong on our side.";
  public static final String LOG_UNEXPECTED_ERROR = "Unexpected error";

  /* ------- security ------- */
  public static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
  public static final String UNAUTHORIZED_DETAIL = "Sign in with a valid access token.";
  public static final String FORBIDDEN_CODE = "FORBIDDEN";
  public static final String FORBIDDEN_DETAIL = "Your role doesn't allow this.";
  public static final String BEARER_CHALLENGE = "Bearer";

  /** Spring MVC's composite exception resolver bean. */
  public static final String EXCEPTION_RESOLVER = "handlerExceptionResolver";
}
