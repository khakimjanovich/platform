package uz.khakimjanovich.platform.idempotency;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum IdempotencyExceptionStatus implements StatusThrowable {
  IDEMPOTENCY_KEY_REUSED,
  IDEMPOTENCY_IN_PROGRESS;

  @Override
  public String getType() {
    return name();
  }
}
