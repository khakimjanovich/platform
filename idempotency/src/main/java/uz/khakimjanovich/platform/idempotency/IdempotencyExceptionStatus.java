package uz.khakimjanovich.platform.idempotency;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum IdempotencyExceptionStatus implements StatusThrowable {
  IDEMPOTENCY_KEY_REUSED;

  @Override
  public String getType() {
    return name();
  }
}
