package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.khakimjanovich.platform.error.ErrorDefinitions;
import uz.khakimjanovich.platform.error.GeneralException;

/** Key names stay inside the service's prefix, and a value that can't work is refused. */
class ValkeyKeysTest {

  private final ValkeyKeys keys = new ValkeyKeys(mock(StringRedisTemplate.class), "customer-gw");

  private static void invalid(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus()).isEqualTo(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT));
  }

  @Test
  void aKeyIsServiceKindAndId() {
    assertThat(keys.key("code-sends", "a1:b2")).isEqualTo("customer-gw:code-sends:a1:b2");
  }

  @Test
  void aKindWithAColonOrUpperCaseOrABlankIdIsRefused() {
    invalid(() -> keys.key("code:sends", "x"));
    invalid(() -> keys.key("Sends", "x"));
    invalid(() -> keys.key("sends", " "));
  }

  @Test
  void aWindowIsWholeSecondsAndALimitAtLeastOne() {
    invalid(() -> keys.increment("sends", "x", Duration.ofMillis(1500)));
    invalid(() -> keys.increment("sends", "x", Duration.ZERO));
    invalid(() -> keys.hit("sends", "x", 0, Duration.ofMinutes(1)));
  }

  @Test
  void everyErrorIsDefinedInEveryLanguage() {
    ErrorDefinitions.assertComplete(
        "uz.khakimjanovich.platform.valkey", "classpath*:properties/valkey-exception.yaml");
  }
}
