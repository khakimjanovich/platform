package uz.heeypay.ecom.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/** A limit that can't work stops startup, naming the property. */
class OtpPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
          .withPropertyValues("ecom.otp.secret=test-secret");

  @Test
  void theDefaultsStart() {
    runner.run(context -> assertThat(context).hasSingleBean(OtpService.class));
  }

  @Test
  void aZeroLengthCodeStopsStartup() {
    runner
        .withPropertyValues("ecom.otp.length=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("ecom.otp")
                    .hasMessageContaining("length"));
  }

  @Test
  void aCodeThatNeverLivesStopsStartup() {
    runner
        .withPropertyValues("ecom.otp.ttl=0s")
        .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("ttl"));
  }
}
