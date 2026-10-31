package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One read per TTL; a client added or taken away shows after it; past it, Vault down throws. */
class ClientRegistryTest {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final String A = "{\"any\": \"record\"}";

  private final VaultSecrets vault = mock(VaultSecrets.class);
  private final TestClock clock = new TestClock();
  private final ClientRegistry registry = new ClientRegistry(vault, "callers", TTL, clock);

  @Test
  void aClientsRecordIsPassedOnUnreadAndReadOncePerTtl() {
    when(vault.get("callers")).thenReturn(Optional.of(Map.of("a", A, "b", "not even JSON")));
    assertThat(registry.find("a")).contains(A);
    assertThat(registry.find("b")).contains("not even JSON");
    assertThat(registry.find("c")).isEmpty();
    assertThat(registry.all()).containsOnlyKeys("a", "b");
    verify(vault, times(1)).get("callers");
  }

  @Test
  void aChangeShowsOnceTheReadIsOld() {
    when(vault.get("callers")).thenReturn(Optional.of(Map.of()));
    assertThat(registry.find("a")).isEmpty();
    when(vault.get("callers")).thenReturn(Optional.of(Map.of("a", A)));
    clock.advance(TTL);
    assertThat(registry.find("a")).contains(A);
    when(vault.get("callers")).thenReturn(Optional.empty());
    clock.advance(TTL);
    assertThat(registry.find("a")).isEmpty();
  }

  @Test
  void vaultDownPastTheTtlIsNoClientNeverTheOldRead() {
    when(vault.get("callers")).thenReturn(Optional.of(Map.of("a", A)));
    assertThat(registry.find("a")).contains(A);
    when(vault.get("callers")).thenThrow(new VaultUnavailableException(new RuntimeException()));
    clock.advance(TTL);
    assertThatThrownBy(() -> registry.find("a")).isInstanceOf(VaultUnavailableException.class);
    assertThatThrownBy(registry::all).isInstanceOf(VaultUnavailableException.class);
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
