package uz.heeypay.ecom.otp;

import uz.heeypay.ecom.error.StatusThrowable;

/** Stable error codes for one-time code failures. */
public enum OtpExceptionStatus implements StatusThrowable {
  OTP_EXPIRED,
  OTP_INVALID,
  OTP_ATTEMPTS_EXCEEDED,
  OTP_SEND_LIMIT,
  OTP_RESEND_TOO_EARLY,
  OTP_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
