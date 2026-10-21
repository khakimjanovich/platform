package uz.heeypay.ecom.otp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstants {

  public static final String PROPERTIES_PREFIX = "ecom.otp";

  /**
   * Redis key: {prefix}:{flow}:{keyed hash of the subject}:{part}. The hash is a Redis Cluster hash
   * tag ({@code {…}}), so one subject's keys share a slot and each script may touch them all.
   */
  public static final String KEY_FORMAT = "%s:%s:{%s}:%s";

  /** Domain separation: a code's hash can never equal a subject's. */
  public static final String HASH_CODE = "code:";

  public static final String HASH_SUBJECT = "subject:";

  public static final String LOG_UNAVAILABLE = "One-time codes unavailable: {}";

  public static final String KEY_CODE = "code";
  public static final String KEY_ATTEMPTS = "attempts";
  public static final String KEY_SENDS = "sends";
  public static final String KEY_LAST = "last";

  public static final String METER_ISSUED = "ecom.otp.issued";
  public static final String METER_VERIFIED = "ecom.otp.verified";
  public static final String METER_REJECTED = "ecom.otp.rejected";
  public static final String TAG_FLOW = "flow";
  public static final String TAG_REASON = "reason";

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
  public static final String DEFAULT_RESEND_AFTER = "60s";
  public static final String DEFAULT_KEY_PREFIX = "otp";
}
