package uz.khakimjanovich.platform.breaker;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum BreakerExceptionStatus implements StatusThrowable {
  BREAKER_OPEN;

  @Override
  public String getType() {
    return name();
  }
}
