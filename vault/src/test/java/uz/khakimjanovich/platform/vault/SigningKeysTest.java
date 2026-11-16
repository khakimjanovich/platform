package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

class SigningKeysTest {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final byte[] KEY = "shop-1-signing-key".getBytes(StandardCharsets.UTF_8);

  private final VaultSecrets vault = mock(VaultSecrets.class);
  private final TestClock clock = new TestClock();
  private final SigningKeys keys = new SigningKeys(vault, "merchants/%s", "signingKey", TTL, clock);

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

  @Test
  void anIdThatIsAPathNeverReachesVault() {
    assertThat(keys.find("../demo")).isEmpty();
    assertThat(keys.find("..")).isEmpty();
    assertThat(keys.find("")).isEmpty();
    verifyNoInteractions(vault);
  }

  @Test
  void cacheDoesNotExceedMaxEntriesUnderBurstOfDistinctClients() {
    var boundedKeys = new SigningKeys(vault, "merchants/%s", "signingKey", TTL, clock, 5);
    for (int i = 1; i <= 20; i++) {
      var id = "shop-" + i;
      var rawKey = ("key-" + i).getBytes(StandardCharsets.UTF_8);
      when(vault.get("merchants/" + id))
          .thenReturn(
              Optional.of(Map.of("signingKey", Base64.getEncoder().encodeToString(rawKey))));
      assertThat(boundedKeys.find(id)).contains(rawKey);
    }
    assertThat(boundedKeys.cacheSize()).isEqualTo(5);
  }

  @Test
  void leastRecentlyUsedKeyIsEvictedWhenCapacityExceeded() {
    var boundedKeys = new SigningKeys(vault, "merchants/%s", "signingKey", TTL, clock, 3);
    for (int i = 1; i <= 3; i++) {
      var id = "shop-" + i;
      var rawKey = ("key-" + i).getBytes(StandardCharsets.UTF_8);
      when(vault.get("merchants/" + id))
          .thenReturn(
              Optional.of(Map.of("signingKey", Base64.getEncoder().encodeToString(rawKey))));
      boundedKeys.find(id);
    }
    assertThat(boundedKeys.cacheSize()).isEqualTo(3);

    // Access shop-1 so shop-2 becomes the least recently used
    boundedKeys.find("shop-1");

    // Add shop-4, which should evict shop-2
    var key4 = "key-4".getBytes(StandardCharsets.UTF_8);
    when(vault.get("merchants/shop-4"))
        .thenReturn(Optional.of(Map.of("signingKey", Base64.getEncoder().encodeToString(key4))));
    boundedKeys.find("shop-4");

    assertThat(boundedKeys.cacheSize()).isEqualTo(3);

    // Querying shop-1 still hits cache (1 vault call total)
    boundedKeys.find("shop-1");
    verify(vault, times(1)).get("merchants/shop-1");

    // Querying shop-2 must miss cache and query vault again (2 vault calls total)
    boundedKeys.find("shop-2");
    verify(vault, times(2)).get("merchants/shop-2");
  }

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
