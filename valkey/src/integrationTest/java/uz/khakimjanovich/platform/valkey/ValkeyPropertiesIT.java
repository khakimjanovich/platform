package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class ValkeyPropertiesIT {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class))
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

  @Test
  void theServiceStarts() {
    runner
        .withPropertyValues("platform.valkey.service=customer-gw")
        .run(context -> assertThat(context).hasSingleBean(ValkeyKeys.class));
  }

  @Test
  void aMissingServiceStopsStartup() {
    runner.run(
        context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("platform.valkey")
                .hasMessageContaining("service"));
  }

  @Test
  void aServiceWithAColonStopsStartup() {
    runner
        .withPropertyValues("platform.valkey.service=customer:gw")
        .run(
            context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("service"));
  }
}
