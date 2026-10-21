package uz.heeypay.ecom.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Without keys or a blind-index key the app stops at startup, naming the property. */
class CryptoPropertiesTest {

  private static final String KEY = key();
  private static final String INDEX = key();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class))
          .withPropertyValues("ecom.crypto.active-key-id=k1");

  @Test
  void completeKeysStart() {
    runner
        .withPropertyValues("ecom.crypto.keys.k1=" + KEY, "ecom.crypto.blind-index-key=" + INDEX)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Crypto.class);
              assertThat(context.getBean(Crypto.class).keyIds()).containsExactly("k1");
            });
  }

  @Test
  void noKeysStopStartup() {
    runner
        .withPropertyValues("ecom.crypto.blind-index-key=" + INDEX)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("ecom.crypto")
                    .hasMessageContaining("keys"));
  }

  @Test
  void noBlindIndexKeyStopsStartup() {
    runner
        .withPropertyValues("ecom.crypto.keys.k1=" + KEY)
        .run(
            context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("blindIndexKey"));
  }

  private static String key() {
    var bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
