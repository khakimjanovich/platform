package uz.heeypay.ecom.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CryptoTest {

  private static final String K1 = key();
  private static final String K2 = key();
  private static final String INDEX = key();
  private static final String PAN = "8600140234567890";

  private final Crypto crypto = new Crypto(new CryptoProperties(Map.of("k1", K1), "k1", INDEX));

  @Test
  void aValueComesBackAndTheCiphertextRevealsNothing() {
    var sealed = crypto.encrypt(PAN);
    assertThat(sealed).startsWith("v1:k1:").doesNotContain("7890");
    assertThat(crypto.decrypt(sealed)).isEqualTo(PAN);
    assertThat(crypto.encrypt(PAN)).as("a fresh nonce each time").isNotEqualTo(sealed);
  }

  @Test
  void aChangedCiphertextDoesNotDecrypt() {
    var sealed = crypto.encrypt(PAN);
    var bytes = Base64.getDecoder().decode(sealed.substring("v1:k1:".length()));
    bytes[bytes.length - 1] ^= 1;
    var tampered = "v1:k1:" + Base64.getEncoder().encodeToString(bytes);
    assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCiphertextCantBeMovedToAnotherKeyId() {
    var both = new Crypto(new CryptoProperties(Map.of("k1", K1, "k2", K1), "k1", INDEX));
    var sealed = both.encrypt(PAN);
    assertThatThrownBy(() -> both.decrypt(sealed.replace("v1:k1:", "v1:k2:")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rotationReadsOldValuesAndRewrapsThem() {
    var old = crypto.encrypt(PAN);
    var rotated = new Crypto(new CryptoProperties(Map.of("k1", K1, "k2", K2), "k2", INDEX));
    assertThat(rotated.isStale(old)).isTrue();
    var rewrapped = rotated.rewrap(old);
    assertThat(rewrapped).startsWith("v1:k2:");
    assertThat(rotated.decrypt(rewrapped)).isEqualTo(PAN);
    assertThat(rotated.rewrap(rewrapped)).isSameAs(rewrapped);
  }

  @Test
  void theBlindIndexFindsEqualValuesWithoutDecrypting() {
    assertThat(crypto.blindIndex(PAN))
        .isEqualTo(crypto.blindIndex(PAN))
        .hasSize(64)
        .doesNotContain(PAN);
    assertThat(crypto.blindIndex(PAN)).isNotEqualTo(crypto.blindIndex("8600140200000082"));
  }

  @Test
  void weakOrMissingKeysStopStartup() {
    assertThatThrownBy(
            () -> new Crypto(new CryptoProperties(Map.of("k1", "c2hvcnQ="), "k1", INDEX)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new Crypto(new CryptoProperties(Map.of("k1", K1), "k9", INDEX)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static String key() {
    var bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
