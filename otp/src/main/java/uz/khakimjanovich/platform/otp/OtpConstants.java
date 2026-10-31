package uz.khakimjanovich.platform.otp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstants {

  public static final String PROPERTIES_PREFIX = "platform.otp";

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

  public static final String METER_ISSUED = "platform.otp.issued";
  public static final String METER_VERIFIED = "platform.otp.verified";
  public static final String METER_REJECTED = "platform.otp.rejected";
  public static final String TAG_FLOW = "flow";
  public static final String TAG_REASON = "reason";

  public static final String HASH_ALGORITHM = "HmacSHA256";
  public static final String ISSUE_SCRIPT = "otp/issue.lua";
  public static final String VERIFY_SCRIPT = "otp/verify.lua";
  public static final String SECRET_MISSING =
      "Set platform.otp.secret: codes are stored as HMACs keyed with it.";
}
