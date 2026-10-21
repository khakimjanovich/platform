package uz.heeypay.ecom.error.fixtures.complete;

import uz.heeypay.ecom.error.StatusThrowable;

/** Its constant has a body, so the enum compiles to an abstract class: it must still be found. */
public enum DemoExceptionStatus implements StatusThrowable {
  DEMO_FAILED {
    @Override
    public String getType() {
      return name();
    }
  }
}
