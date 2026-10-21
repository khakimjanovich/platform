package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One Vault read per client per TTL; a missing key is asked again; a failure never goes stale. */
class SigningKeysTest {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final byte[] KEY = "shop-1-signing-key".getBytes(StandardCharsets.UTF_8);

  private final VaultSecrets vault = mock(VaultSecrets.class);
  private final TestClock clock = new TestClock();
  private final SigningKeys keys = new SigningKeys(vault, "merchants/%s", TTL, clock);

  private void vaultHas(byte[] key) {
    when(vault.get("merchants/shop-1"))
        .thenReturn(Optional.of(Map.of("signingKey", Base64.getEncoder().encodeToString(key))));
  }

  @Test
  void theKeyIsDecodedFromBase64() {
    vaultHas(KEY);
    assertThat(keys.find("shop-1")).contains(KEY);
  }

  @Test
  void aBurstOfRequestsReadsVaultOnce() {
    vaultHas(KEY);
    for (int i = 0; i < 100; i++) assertThat(keys.find("shop-1")).contains(KEY);
    verify(vault, times(1)).get("merchants/shop-1");
  }

  @Test
  void aKeyIsReadAgainAfterTheTtl() {
    vaultHas(KEY);
    keys.find("shop-1");
    clock.advance(TTL.plusSeconds(1));
    var rotated = "rotated-key".getBytes(StandardCharsets.UTF_8);
    vaultHas(rotated);
    assertThat(keys.find("shop-1")).contains(rotated);
    verify(vault, times(2)).get("merchants/shop-1");
  }

  @Test
  void aMissingKeyIsNeverRemembered() {
    when(vault.get("merchants/shop-1")).thenReturn(Optional.empty());
    assertThat(keys.find("shop-1")).isEmpty();
    assertThat(keys.find("shop-1")).isEmpty();
    verify(vault, times(2)).get("merchants/shop-1");
  }

  @Test
  void aVaultFailureIsNeverAnsweredWithAnOldKey() {
    vaultHas(KEY);
    keys.find("shop-1");
    clock.advance(TTL.plusSeconds(1));
    when(vault.get("merchants/shop-1"))
        .thenThrow(new VaultUnavailableException(new IllegalStateException("sealed")));
    assertThatThrownBy(() -> keys.find("shop-1")).isInstanceOf(VaultUnavailableException.class);
  }

  /** A clock the test moves. */
  private static final class TestClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
