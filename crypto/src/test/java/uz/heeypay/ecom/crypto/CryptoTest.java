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

  @Test
  void aV2ValueReadsOnlyWhereItLives() {
    var pan = crypto.encrypt(PAN, "card.pan:card-1");
    assertThat(pan).startsWith("v2:k1:");
    assertThat(crypto.decrypt(pan, "card.pan:card-1")).isEqualTo(PAN);
    assertThatThrownBy(() -> crypto.decrypt(pan, "card.pan:card-2"))
        .as("copied to another card")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> crypto.decrypt(pan, "card.expiry:card-1"))
        .as("copied to another field")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> crypto.decrypt(pan))
        .as("never without its context")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void v1DataStillReadsAndRewrapsToV2() {
    var old = crypto.encrypt(PAN);
    assertThat(crypto.decrypt(old)).isEqualTo(PAN);
    assertThat(crypto.decrypt(old, "card.pan:card-1")).as("v1 has no context").isEqualTo(PAN);
    var upgraded = crypto.rewrap(old, "card.pan:card-1");
    assertThat(upgraded).startsWith("v2:k1:");
    assertThat(crypto.decrypt(upgraded, "card.pan:card-1")).isEqualTo(PAN);
    assertThat(crypto.rewrap(upgraded, "card.pan:card-1"))
        .as("already current")
        .isEqualTo(upgraded);
  }

  @Test
  void aV2ValueUnderAnOldKeyMovesToTheActiveOne() {
    var before = new Crypto(new CryptoProperties(Map.of("k1", K1, "k2", K2), "k1", INDEX));
    var after = new Crypto(new CryptoProperties(Map.of("k1", K1, "k2", K2), "k2", INDEX));
    var sealed = before.encrypt(PAN, "card.pan:card-1");
    assertThat(after.isStale(sealed)).isTrue();
    var rewrapped = after.rewrap(sealed, "card.pan:card-1");
    assertThat(rewrapped).startsWith("v2:k2:");
    assertThat(after.decrypt(rewrapped, "card.pan:card-1")).isEqualTo(PAN);
  }
}
