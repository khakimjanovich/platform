package uz.khakimjanovich.platform.error.fixtures.missinglanguage;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum DemoExceptionStatus implements StatusThrowable {
  DEMO_FAILED;

  @Override
  public String getType() {
    return name();
  }
}
