package uz.heeypay.ecom.otp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstantUtils {

  public static final String PROPERTIES_PREFIX = "ecom.otp";

  /** Redis key: otp:{flow}:{subject}:{part}. */
  public static final String KEY_FORMAT = "otp:%s:%s:%s";
  public static final String KEY_CODE = "code";
  public static final String KEY_ATTEMPTS = "attempts";
  public static final String KEY_SENDS = "sends";

  public static final String HASH_ALGORITHM = "SHA-256";

  public static final String DEFAULT_LENGTH = "6";
  public static final String DEFAULT_TTL = "5m";
  public static final String DEFAULT_MAX_ATTEMPTS = "5";
  public static final String DEFAULT_MAX_SENDS = "3";
  public static final String DEFAULT_SEND_WINDOW = "15m";

  public static final String EXPIRED_DETAIL = "The code has expired or was never sent. Request a new one.";
  public static final String INVALID_DETAIL = "The code is wrong.";
  public static final String ATTEMPTS_EXCEEDED_DETAIL = "Too many wrong codes. Request a new one later.";
  public static final String SEND_LIMIT_DETAIL = "Too many codes sent. Try again later.";
}
