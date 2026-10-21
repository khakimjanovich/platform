package uz.heeypay.ecom.error.fixtures.placeholder;

import uz.heeypay.ecom.error.StatusThrowable;

public enum DemoExceptionStatus implements StatusThrowable {
  DEMO_FAILED;

  @Override
  public String getType() {
    return name();
  }
}
