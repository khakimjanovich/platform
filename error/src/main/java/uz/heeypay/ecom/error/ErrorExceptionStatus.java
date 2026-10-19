package uz.heeypay.ecom.error;

/** Errors every service shares: unexpected failures, invalid input, missing or weak access. */
public enum ErrorExceptionStatus implements StatusThrowable {
  INTERNAL_ERROR,
  VALIDATION_FAILED,
  UNAUTHORIZED,
  FORBIDDEN;

  @Override
  public String getType() {
    return name();
  }
}
