package uz.heeypay.ecom.error;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorStarterConstantUtils {

  /** Extra property on every problem detail: the stable, machine-readable error code. */
  public static final String CODE_PROPERTY = "code";

  public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
  public static final String INTERNAL_ERROR_DETAIL = "Something went wrong on our side.";
  public static final String LOG_UNEXPECTED_ERROR = "Unexpected error";
}
