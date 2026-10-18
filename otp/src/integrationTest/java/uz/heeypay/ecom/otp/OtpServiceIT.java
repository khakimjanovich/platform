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
import uz.heeypay.ecom.error.ApiException;
import uz.heeypay.ecom.error.ErrorConstantUtils;

/** The one-time code rules, checked against a real Redis. */
@Testcontainers
class OtpServiceIT {

  private static final String FLOW = "registration";
  private static final String EMAIL = "aziza@example.uz";

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

  private StringRedisTemplate redis;
  private OtpService otp;

  @BeforeEach
  void setUp() {
    var connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connection.afterPropertiesSet();
    redis = new StringRedisTemplate(connection);
    redis.afterPropertiesSet();
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    otp = new OtpService(redis, new OtpProperties(6, Duration.ofMinutes(5), 3, 2, Duration.ofMinutes(15)));
  }

  @Test
  void aCodeWorksOnce() {
    var code = otp.issue(FLOW, EMAIL);
    otp.verify(FLOW, EMAIL, code);
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code)).satisfies(e -> assertCode(e, OtpErrorCode.EXPIRED));
  }

  @Test
  void onlyTheHashIsStored() {
    var code = otp.issue(FLOW, EMAIL);
    var stored = redis.opsForValue().get(OtpConstantUtils.KEY_FORMAT.formatted(FLOW, EMAIL, OtpConstantUtils.KEY_CODE));
    assertThat(stored).isNotEqualTo(code).hasSize(64);
  }

  @Test
  void wrongCodesLockAfterTheLimit() {
    var code = otp.issue(FLOW, EMAIL);
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, "000000")).satisfies(e -> assertCode(e, OtpErrorCode.INVALID));
    }
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .satisfies(e -> assertCode(e, OtpErrorCode.ATTEMPTS_EXCEEDED));
  }

  @Test
  void sendsAreCapped() {
    otp.issue(FLOW, EMAIL);
    otp.issue(FLOW, EMAIL);
    assertThatThrownBy(() -> otp.issue(FLOW, EMAIL)).satisfies(e -> assertCode(e, OtpErrorCode.SEND_LIMIT));
  }

  private static void assertCode(Throwable e, String code) {
    assertThat(((ApiException) e).getBody().getProperties()).containsEntry(ErrorConstantUtils.CODE_PROPERTY, code);
  }
}
