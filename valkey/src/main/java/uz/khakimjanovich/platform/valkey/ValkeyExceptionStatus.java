package uz.khakimjanovich.platform.valkey;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * Stable error codes for Valkey key failures: Valkey can't be reached or refuses, or a module gave
 * a kind, id, window or limit that can't work.
 */
public enum ValkeyExceptionStatus implements StatusThrowable {
  VALKEY_UNAVAILABLE,
  VALKEY_INVALID_ARGUMENT;

  @Override
  public String getType() {
    return name();
  }
}
