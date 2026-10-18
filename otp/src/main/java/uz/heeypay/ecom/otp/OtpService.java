package uz.heeypay.ecom.otp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import uz.heeypay.ecom.error.ApiException;

/**
 * Issues and checks one-time codes. Redis keeps only a keyed hash (HMAC-SHA256 with a configured
 * secret) of each code, so a copy of Redis alone can't be brute-forced; the subject in each key is
 * hashed the same way. Issuing and checking each run as one Lua script, so limits hold and a code
 * works once even under concurrent requests.
 */
public class OtpService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final RedisScript<Long> ISSUE =
      RedisScript.of(new ClassPathResource(OtpConstantUtils.ISSUE_SCRIPT), Long.class);
  private static final RedisScript<Long> VERIFY =
      RedisScript.of(new ClassPathResource(OtpConstantUtils.VERIFY_SCRIPT), Long.class);

  private final StringRedisTemplate redis;
  private final OtpProperties properties;
  private final SecretKeySpec key;

  public OtpService(StringRedisTemplate redis, OtpProperties properties) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException(OtpConstantUtils.SECRET_MISSING);
    }
    this.redis = redis;
    this.properties = properties;
    this.key =
        new SecretKeySpec(
            properties.secret().getBytes(StandardCharsets.UTF_8), OtpConstantUtils.HASH_ALGORITHM);
  }

  /**
   * A new code for this flow and subject (e.g. "registration", an email). The caller delivers it.
   */
  public String issue(String flow, String subject) {
    var code = randomDigits(properties.length());
    var sends =
        redis.execute(
            ISSUE,
            keys(
                flow,
                subject,
                OtpConstantUtils.KEY_SENDS,
                OtpConstantUtils.KEY_CODE,
                OtpConstantUtils.KEY_ATTEMPTS),
            String.valueOf(properties.maxSends()),
            String.valueOf(properties.sendWindow().toMillis()),
            hash(code),
            String.valueOf(properties.ttl().toMillis()));
    if (sends == null || sends < 0) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          OtpErrorCode.SEND_LIMIT,
          OtpConstantUtils.SEND_LIMIT_DETAIL);
    }
    return code;
  }

  /** Returns normally if the code is right; the code can then never be used again. */
  public void verify(String flow, String subject, String code) {
    var result =
        redis.execute(
            VERIFY,
            keys(flow, subject, OtpConstantUtils.KEY_CODE, OtpConstantUtils.KEY_ATTEMPTS),
            String.valueOf(properties.maxAttempts()),
            String.valueOf(properties.ttl().toMillis()),
            hash(code));
    switch (result == null ? 0 : result.intValue()) {
      case 1 -> {}
      case -1 ->
          throw new ApiException(
              HttpStatus.BAD_REQUEST, OtpErrorCode.INVALID, OtpConstantUtils.INVALID_DETAIL);
      case -2 ->
          throw new ApiException(
              HttpStatus.TOO_MANY_REQUESTS,
              OtpErrorCode.ATTEMPTS_EXCEEDED,
              OtpConstantUtils.ATTEMPTS_EXCEEDED_DETAIL);
      default ->
          throw new ApiException(
              HttpStatus.GONE, OtpErrorCode.EXPIRED, OtpConstantUtils.EXPIRED_DETAIL);
    }
  }

  /** The Redis key for each part; the subject (often an email) is stored only as a keyed hash. */
  private List<String> keys(String flow, String subject, String... parts) {
    var hashedSubject = hash(subject);
    return java.util.Arrays.stream(parts)
        .map(part -> OtpConstantUtils.KEY_FORMAT.formatted(flow, hashedSubject, part))
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
      var mac = Mac.getInstance(OtpConstantUtils.HASH_ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }
}
