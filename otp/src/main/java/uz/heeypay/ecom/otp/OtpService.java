package uz.heeypay.ecom.otp;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import uz.heeypay.ecom.error.GeneralException;

/**
 * Issues and checks one-time codes. Redis keeps only a keyed hash (HMAC-SHA256 with a configured
 * secret) of each code, so a copy of Redis alone can't be brute-forced; the subject in each key is
 * hashed the same way. Issuing and checking each run as one Lua script, so limits hold and a code
 * works once even under concurrent requests.
 *
 * <p>Wrong codes count for the whole send window, across codes, so a lockout holds until the window
 * ends; a new code waits {@code resend-after} after the last one. Every refusal that lifts by
 * itself says when ({@code Retry-After}). Without Redis nothing is issued or accepted: {@code
 * OTP_UNAVAILABLE}.
 */
@Slf4j
public class OtpService {

  private static final SecureRandom RANDOM = new SecureRandom();

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> ISSUE =
      RedisScript.of(new ClassPathResource(OtpConstants.ISSUE_SCRIPT), List.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> VERIFY =
      RedisScript.of(new ClassPathResource(OtpConstants.VERIFY_SCRIPT), List.class);

  private final StringRedisTemplate redis;
  private final OtpProperties properties;
  private final MeterRegistry meters;
  private final SecretKeySpec key;

  public OtpService(StringRedisTemplate redis, OtpProperties properties, MeterRegistry meters) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException(OtpConstants.SECRET_MISSING);
    }
    this.redis = redis;
    this.properties = properties;
    this.meters = meters;
    this.key =
        new SecretKeySpec(
            properties.secret().getBytes(StandardCharsets.UTF_8), OtpConstants.HASH_ALGORITHM);
  }

  /**
   * A new code for this flow and subject (e.g. "registration", an email). The caller delivers it.
   */
  public String issue(String flow, String subject) {
    var code = randomDigits(properties.length());
    var result =
        run(
            () ->
                redis.execute(
                    ISSUE,
                    keys(
                        flow,
                        subject,
                        OtpConstants.KEY_SENDS,
                        OtpConstants.KEY_CODE,
                        OtpConstants.KEY_ATTEMPTS,
                        OtpConstants.KEY_LAST),
                    String.valueOf(properties.maxSends()),
                    String.valueOf(properties.sendWindow().toMillis()),
                    hash(OtpConstants.HASH_CODE + code),
                    String.valueOf(properties.ttl().toMillis()),
                    String.valueOf(properties.maxAttempts()),
                    String.valueOf(properties.resendAfter().toMillis())));
    switch (status(result)) {
      case -1 -> throw refused(OtpExceptionStatus.OTP_SEND_LIMIT, result);
      case -2 -> throw refused(OtpExceptionStatus.OTP_RESEND_TOO_EARLY, result);
      case -3 -> throw refused(OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, result);
      default -> meters.counter(OtpConstants.METER_ISSUED, OtpConstants.TAG_FLOW, flow).increment();
    }
    return code;
  }

  /** Returns normally if the code is right; the code can then never be used again. */
  public void verify(String flow, String subject, String code) {
    var result =
        run(
            () ->
                redis.execute(
                    VERIFY,
                    keys(flow, subject, OtpConstants.KEY_CODE, OtpConstants.KEY_ATTEMPTS),
                    String.valueOf(properties.maxAttempts()),
                    String.valueOf(properties.sendWindow().toMillis()),
                    hash(OtpConstants.HASH_CODE + code)));
    switch (status(result)) {
      case 1 ->
          meters.counter(OtpConstants.METER_VERIFIED, OtpConstants.TAG_FLOW, flow).increment();
      case -1 -> throw rejected(flow, new GeneralException(OtpExceptionStatus.OTP_INVALID));
      case -2 -> throw rejected(flow, refused(OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, result));
      default -> throw rejected(flow, new GeneralException(OtpExceptionStatus.OTP_EXPIRED));
    }
  }

  private GeneralException rejected(String flow, GeneralException e) {
    meters
        .counter(
            OtpConstants.METER_REJECTED,
            OtpConstants.TAG_FLOW,
            flow,
            OtpConstants.TAG_REASON,
            e.getStatus().getType())
        .increment();
    return e;
  }

  /** A refusal that lifts by itself: Retry-After is the Redis key's remaining lifetime. */
  private static GeneralException refused(OtpExceptionStatus status, List<?> result) {
    var wait = Math.max(1, ((Number) result.get(1)).longValue());
    return new GeneralException(status).retryAfter(Duration.ofMillis(wait));
  }

  private static int status(List<?> result) {
    return result == null || result.isEmpty() ? 0 : ((Number) result.getFirst()).intValue();
  }

  /** Fail closed: without Redis, no code is issued or accepted; the log says why. */
  private static List<?> run(Supplier<List<?>> script) {
    try {
      return script.get();
    } catch (DataAccessException e) {
      log.warn(OtpConstants.LOG_UNAVAILABLE, e.getMessage(), e);
      throw new GeneralException(OtpExceptionStatus.OTP_UNAVAILABLE);
    }
  }

  /** The Redis key for each part; the subject (often an email) is stored only as a keyed hash. */
  private List<String> keys(String flow, String subject, String... parts) {
    var hashedSubject = hash(OtpConstants.HASH_SUBJECT + subject);
    return Arrays.stream(parts)
        .map(
            part ->
                OtpConstants.KEY_FORMAT.formatted(
                    properties.keyPrefix(), flow, hashedSubject, part))
        .toList();
  }

  private static String randomDigits(int length) {
    var digits = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      digits.append(RANDOM.nextInt(10));
    }
    return digits.toString();
  }

  private String hash(String value) {
    try {
      var mac = Mac.getInstance(OtpConstants.HASH_ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }
}
