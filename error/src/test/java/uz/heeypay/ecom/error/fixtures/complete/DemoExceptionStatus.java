package uz.heeypay.ecom.error.fixtures.complete;

import uz.heeypay.ecom.error.StatusThrowable;

public enum DemoExceptionStatus implements StatusThrowable {
  DEMO_FAILED;

  @Override
  public String getType() {
    return name();
  }
}
