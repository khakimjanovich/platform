package uz.heeypay.ecom.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import uz.heeypay.ecom.error.GeneralException;

/** How the scripts' answers become codes and errors, without Redis. */
class OtpServiceTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private static OtpProperties properties(String secret, int length) {
    return new OtpProperties(
        secret,
        length,
        Duration.ofMinutes(5),
        5,
        3,
        Duration.ofMinutes(15),
        Duration.ofSeconds(60),
        "otp");
  }

  private final OtpService otp = new OtpService(redis, properties("test-secret", 6), meters);

  private void scriptAnswers(long status, long millis) {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of(status, millis));
  }

  @Test
  void aCodeHasTheConfiguredLengthAndOnlyDigits() {
    scriptAnswers(1, 0);
    assertThat(otp.issue("registration", "aziz@example.uz")).matches("\\d{6}");
    var eight = new OtpService(redis, properties("test-secret", 8), meters);
    assertThat(eight.issue("registration", "aziz@example.uz")).matches("\\d{8}");
    assertThat(meters.counter("ecom.otp.issued", "flow", "registration").count()).isEqualTo(2);
  }

  @Test
  void eachRefusalIsItsCodeWithRetryAfter() {
    scriptAnswers(-1, 600_000);
    assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_SEND_LIMIT, 600);
    scriptAnswers(-2, 41_500);
    assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_RESEND_TOO_EARLY, 41.5);
    scriptAnswers(-3, 120_000);
    assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, 120);
    scriptAnswers(-2, 30_000);
    assertRefused(
        () -> otp.verify("f", "s", "123456"), OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, 30);
  }

  @Test
  void aWrongOrExpiredCodeHasNoRetryAfter() {
    scriptAnswers(-1, 0);
    assertThatThrownBy(() -> otp.verify("f", "s", "000000"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(OtpExceptionStatus.OTP_INVALID);
              assertThat(e.getRetryAfter()).isNull();
            });
    scriptAnswers(0, 0);
    assertThatThrownBy(() -> otp.verify("f", "s", "000000"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(OtpExceptionStatus.OTP_EXPIRED));
    assertThat(meters.counter("ecom.otp.rejected", "flow", "f", "reason", "OTP_INVALID").count())
        .isEqualTo(1);
  }

  @Test
  void withoutRedisNothingIsIssuedOrAccepted() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new RedisConnectionFailureException("refused"));
    assertThatThrownBy(() -> otp.issue("f", "s"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(OtpExceptionStatus.OTP_UNAVAILABLE));
    assertThatThrownBy(() -> otp.verify("f", "s", "123456"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(OtpExceptionStatus.OTP_UNAVAILABLE));
  }

  @Test
  void aServiceWithoutASecretRefusesToStart() {
    assertThatThrownBy(() -> new OtpService(redis, properties(" ", 6), meters))
        .isInstanceOf(IllegalStateException.class);
  }

  private static void assertRefused(Runnable call, OtpExceptionStatus status, double seconds) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(status);
              assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMillis((long) (seconds * 1000)));
            });
  }
}
