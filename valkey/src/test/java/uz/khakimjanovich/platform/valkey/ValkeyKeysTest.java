package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Key names stay inside the service's prefix, and an unworkable value stops the call or startup.
 */
class ValkeyKeysTest {

  private final ValkeyKeys keys = new ValkeyKeys(mock(StringRedisTemplate.class), "customer-gw");

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValkeyAutoConfiguration.class))
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

  @Test
  void aKeyIsServiceKindAndId() {
    assertThat(keys.key("code-sends", "a1:b2")).isEqualTo("customer-gw:code-sends:a1:b2");
  }

  @Test
  void aKindWithAColonOrUpperCaseIsRefused() {
    assertThatThrownBy(() -> keys.key("code:sends", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> keys.key("Sends", "x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBlankIdIsRefused() {
    assertThatThrownBy(() -> keys.key("sends", " ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWindowIsWholeSecondsAndALimitAtLeastOne() {
    assertThatThrownBy(() -> keys.increment("sends", "x", Duration.ofMillis(1500)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> keys.increment("sends", "x", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> keys.hit("sends", "x", 0, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

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
