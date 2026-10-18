package uz.heeypay.ecom.otp;

import lombok.experimental.UtilityClass;

/** Stable error codes for one-time code failures. */
@UtilityClass
public class OtpErrorCode {

  public static final String EXPIRED = "OTP_EXPIRED";
  public static final String INVALID = "OTP_INVALID";
  public static final String ATTEMPTS_EXCEEDED = "OTP_ATTEMPTS_EXCEEDED";
  public static final String SEND_LIMIT = "OTP_SEND_LIMIT";
}
