package uz.khakimjanovich.platform.valkey;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum ValkeyExceptionStatus implements StatusThrowable {
  VALKEY_UNAVAILABLE,
  VALKEY_INVALID_ARGUMENT,
  VALKEY_LOCK_HELD;

  @Override
  public String getType() {
    return name();
  }
}
