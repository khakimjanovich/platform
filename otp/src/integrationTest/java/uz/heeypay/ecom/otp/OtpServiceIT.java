package uz.heeypay.ecom.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.heeypay.ecom.error.GeneralException;

/** The one-time code rules, checked against a real Redis. */
@Testcontainers
class OtpServiceIT {

  private static final String FLOW = "registration";
  private static final String EMAIL = "aziza@example.uz";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

  private StringRedisTemplate redis;
  private OtpService otp;

  @BeforeEach
  void setUp() {
    var connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connection.afterPropertiesSet();
    redis = new StringRedisTemplate(connection);
    redis.afterPropertiesSet();
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    otp =
        new OtpService(
            redis,
            new OtpProperties(
                "test-secret", 6, Duration.ofMinutes(5), 3, 2, Duration.ofMinutes(15)));
  }

  @Test
  void aCodeWorksOnce() {
    var code = otp.issue(FLOW, EMAIL);
    otp.verify(FLOW, EMAIL, code);
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_EXPIRED.getType()));
  }

  @Test
  void neitherTheCodeNorTheEmailIsStored() {
    var code = otp.issue(FLOW, EMAIL);
    var keys = redis.keys("otp:*");
    assertThat(keys).isNotEmpty().noneMatch(key -> key.contains(EMAIL));
    assertThat(keys.stream().map(key -> redis.opsForValue().get(key))).noneMatch(code::equals);
  }

  @Test
  void aCodeWorksOnceEvenUnderConcurrentRequests() throws Exception {
    var code = otp.issue(FLOW, EMAIL);
    var accepted = new java.util.concurrent.atomic.AtomicInteger();
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new java.util.concurrent.CountDownLatch(1);
      for (int i = 0; i < 20; i++) {
        pool.submit(
            () -> {
              start.await();
              try {
                otp.verify(FLOW, EMAIL, code);
                accepted.incrementAndGet();
              } catch (GeneralException ignored) {
                // expired: someone else used it first
              }
              return null;
            });
      }
      start.countDown();
    }
    assertThat(accepted).hasValue(1);
  }

  @Test
  void aServiceWithoutASecretRefusesToStart() {
    assertThatThrownBy(
            () ->
                new OtpService(
                    redis,
                    new OtpProperties(" ", 6, Duration.ofMinutes(5), 3, 2, Duration.ofMinutes(15))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void wrongCodesLockAfterTheLimit() {
    var code = otp.issue(FLOW, EMAIL);
    var wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, wrong))
          .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_INVALID.getType()));
    }
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED.getType()));
  }

  @Test
  void sendsAreCapped() {
    otp.issue(FLOW, EMAIL);
    otp.issue(FLOW, EMAIL);
    assertThatThrownBy(() -> otp.issue(FLOW, EMAIL))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_SEND_LIMIT.getType()));
  }

  private static void assertCode(Throwable e, String code) {
    assertThat(((GeneralException) e).getStatus().getType()).isEqualTo(code);
  }
}
