package uz.heeypay.ecom.error.fixtures.leftover;

import uz.heeypay.ecom.error.StatusThrowable;

public enum DemoExceptionStatus implements StatusThrowable {
  DEMO_FAILED;

  @Override
  public String getType() {
    return name();
  }
}
