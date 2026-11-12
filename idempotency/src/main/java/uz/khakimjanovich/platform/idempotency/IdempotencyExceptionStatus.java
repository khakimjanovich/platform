package uz.khakimjanovich.platform.idempotency;

import uz.khakimjanovich.platform.error.StatusThrowable;

/** Stable error codes for idempotency failures. */
public enum IdempotencyExceptionStatus implements StatusThrowable {
  IDEMPOTENCY_KEY_REUSED;

  @Override
  public String getType() {
    return name();
  }
}
