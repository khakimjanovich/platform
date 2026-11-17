package uz.khakimjanovich.platform.otp;

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
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.valkey.ValkeyExceptionStatus;
import uz.khakimjanovich.platform.valkey.ValkeyKeys;
import uz.khakimjanovich.platform.valkey.ValkeyScript;

@Slf4j
public class OtpService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final ValkeyScript<List<Long>> ISSUE = script(OtpConstants.ISSUE_SCRIPT);
  private static final ValkeyScript<List<Long>> VERIFY = script(OtpConstants.VERIFY_SCRIPT);

  private final ValkeyKeys valkey;
  private final OtpProperties properties;
  private final MeterRegistry meters;
  private final SecretKeySpec key;

  public OtpService(ValkeyKeys valkey, OtpProperties properties, MeterRegistry meters) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException(OtpConstants.SECRET_MISSING);
    }
    this.valkey = valkey;
    this.properties = properties;
    this.meters = meters;
    this.key =
        new SecretKeySpec(
            properties.secret().getBytes(StandardCharsets.UTF_8), OtpConstants.HASH_ALGORITHM);
  }

  public String issue(String flow, String subject) {
    var code = randomDigits(properties.length());
    var result =
        run(
            () ->
                valkey.execute(
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

  public void verify(String flow, String subject, String code) {
    var result =
        run(
            () ->
                valkey.execute(
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

  private static GeneralException refused(OtpExceptionStatus status, List<Long> result) {
    var wait = Math.max(1, result.get(1));
    return new GeneralException(status).retryAfter(Duration.ofMillis(wait));
  }

  private static int status(List<Long> result) {
    return result == null || result.isEmpty() ? 0 : result.getFirst().intValue();
  }

  private static List<Long> run(Supplier<List<Long>> script) {
    try {
      return script.get();
    } catch (GeneralException e) {
      if (e.getStatus() == ValkeyExceptionStatus.VALKEY_UNAVAILABLE) {
        log.warn(OtpConstants.LOG_UNAVAILABLE, e.getMessage(), e);
        throw new GeneralException(OtpExceptionStatus.OTP_UNAVAILABLE);
      }
      throw e;
    } catch (RuntimeException e) {
      log.warn(OtpConstants.LOG_UNAVAILABLE, e.getMessage(), e);
      throw new GeneralException(OtpExceptionStatus.OTP_UNAVAILABLE);
    }
  }

  // The cast changes only the static type: Spring uses the class to choose a multi-bulk reply.
  @SuppressWarnings("unchecked")
  private static ValkeyScript<List<Long>> script(String path) {
    return ValkeyScript.ofClassPath(path, (Class<List<Long>>) (Class<?>) List.class);
  }

  private List<String> keys(String flow, String subject, String... parts) {
    var prefix =
        (properties.keyPrefix() != null && !properties.keyPrefix().isBlank())
            ? properties.keyPrefix()
            : valkey.service();
    var hashedSubject = hash(OtpConstants.HASH_SUBJECT + subject);
    return Arrays.stream(parts)
        .map(part -> OtpConstants.KEY_FORMAT.formatted(prefix, flow, hashedSubject, part))
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
