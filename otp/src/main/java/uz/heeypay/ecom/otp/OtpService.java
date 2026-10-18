package uz.heeypay.ecom.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import uz.heeypay.ecom.error.ApiException;

/**
 * Issues and checks one-time codes. Redis keeps only a hash of each code, with its lifetime;
 * wrong attempts and sends are counted, and both are capped by configuration.
 */
@RequiredArgsConstructor
public class OtpService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;
  private final OtpProperties properties;

  /** A new code for this flow and subject (e.g. "registration", an email). The caller delivers it. */
  public String issue(String flow, String subject) {
    var sends = redis.opsForValue().increment(key(flow, subject, OtpConstantUtils.KEY_SENDS));
    if (sends != null && sends == 1) {
      redis.expire(key(flow, subject, OtpConstantUtils.KEY_SENDS), properties.sendWindow());
    }
    if (sends != null && sends > properties.maxSends()) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, OtpErrorCode.SEND_LIMIT, OtpConstantUtils.SEND_LIMIT_DETAIL);
    }
    var code = randomDigits(properties.length());
    redis.opsForValue().set(key(flow, subject, OtpConstantUtils.KEY_CODE), hash(code), properties.ttl());
    redis.delete(key(flow, subject, OtpConstantUtils.KEY_ATTEMPTS));
    return code;
  }

  /** Returns normally if the code is right; the code can then never be used again. */
  public void verify(String flow, String subject, String code) {
    var stored = redis.opsForValue().get(key(flow, subject, OtpConstantUtils.KEY_CODE));
    if (stored == null) {
      throw new ApiException(HttpStatus.GONE, OtpErrorCode.EXPIRED, OtpConstantUtils.EXPIRED_DETAIL);
    }
    var attemptsKey = key(flow, subject, OtpConstantUtils.KEY_ATTEMPTS);
    var attempts = redis.opsForValue().increment(attemptsKey);
    if (attempts != null && attempts == 1) {
      redis.expire(attemptsKey, properties.ttl());
    }
    if (attempts != null && attempts > properties.maxAttempts()) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, OtpErrorCode.ATTEMPTS_EXCEEDED, OtpConstantUtils.ATTEMPTS_EXCEEDED_DETAIL);
    }
    if (!MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), hash(code).getBytes(StandardCharsets.UTF_8))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, OtpErrorCode.INVALID, OtpConstantUtils.INVALID_DETAIL);
    }
    redis.delete(key(flow, subject, OtpConstantUtils.KEY_CODE));
    redis.delete(attemptsKey);
  }

  private static String key(String flow, String subject, String part) {
    return OtpConstantUtils.KEY_FORMAT.formatted(flow, subject, part);
  }

  private static String randomDigits(int length) {
    var digits = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      digits.append(RANDOM.nextInt(10));
    }
    return digits.toString();
  }

  private static String hash(String code) {
    try {
      var digest = MessageDigest.getInstance(OtpConstantUtils.HASH_ALGORITHM);
      return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
