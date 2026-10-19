package uz.heeypay.ecom.otp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstants {

  public static final String PROPERTIES_PREFIX = "ecom.otp";

  /** Redis key: otp:{flow}:{keyed hash of the subject}:{part}. */
  public static final String KEY_FORMAT = "otp:%s:%s:%s";

  public static final String KEY_CODE = "code";
  public static final String KEY_ATTEMPTS = "attempts";
  public static final String KEY_SENDS = "sends";

  public static final String HASH_ALGORITHM = "HmacSHA256";
  public static final String ISSUE_SCRIPT = "otp/issue.lua";
  public static final String VERIFY_SCRIPT = "otp/verify.lua";
  public static final String SECRET_MISSING =
      "Set ecom.otp.secret: codes are stored as HMACs keyed with it.";

  public static final String DEFAULT_LENGTH = "6";
  public static final String DEFAULT_TTL = "5m";
  public static final String DEFAULT_MAX_ATTEMPTS = "5";
  public static final String DEFAULT_MAX_SENDS = "3";
  public static final String DEFAULT_SEND_WINDOW = "15m";
}
