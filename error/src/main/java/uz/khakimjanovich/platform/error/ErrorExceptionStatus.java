package uz.khakimjanovich.platform.error;

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
