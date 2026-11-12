package uz.khakimjanovich.platform.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class OtpPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
          .withPropertyValues(
              "platform.otp.secret=test-secret",
              "platform.otp.length=6",
              "platform.otp.ttl=5m",
              "platform.otp.max-attempts=5",
              "platform.otp.max-sends=3",
              "platform.otp.send-window=15m",
              "platform.otp.resend-after=60s",
              "platform.otp.key-prefix=otp");

  @Test
  void everyLimitSetStarts() {
    runner.run(context -> assertThat(context).hasSingleBean(OtpService.class));
  }

  @Test
  void aMissingLimitStopsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues("platform.otp.secret=test-secret")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.otp")
                    .hasMessageContaining("length")
                    .hasMessageContaining("ttl")
                    .hasMessageContaining("maxAttempts")
                    .hasMessageContaining("maxSends")
                    .hasMessageContaining("sendWindow")
                    .hasMessageContaining("resendAfter")
                    .hasMessageContaining("keyPrefix"));
  }

  @Test
  void aZeroLengthCodeStopsStartup() {
    runner
        .withPropertyValues("platform.otp.length=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.otp")
                    .hasMessageContaining("length"));
  }

  @Test
  void aCodeThatNeverLivesStopsStartup() {
    runner
        .withPropertyValues("platform.otp.ttl=0s")
        .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("ttl"));
  }
}
