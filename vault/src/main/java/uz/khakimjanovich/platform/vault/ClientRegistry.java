package uz.khakimjanovich.platform.vault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class ClientRegistry {

  private final VaultSecrets vault;
  private final String path;
  private final Duration ttl;
  private final Clock clock;
  private volatile @Nullable Read read;

  private record Read(Map<String, String> clients, Instant until) {}

  public ClientRegistry(VaultSecrets vault, String path, Duration ttl, Clock clock) {
    this.vault = vault;
    this.path = path;
    this.ttl = ttl;
    this.clock = clock;
  }

  public Optional<String> find(String clientId) {
    return Optional.ofNullable(all().get(clientId));
  }

  public Map<String, String> all() {
    var now = clock.instant();
    var last = read;
    if (last != null && now.isBefore(last.until())) return last.clients();
    read = null;
    var clients = Map.copyOf(vault.get(path).orElse(Map.of()));
    read = new Read(clients, now.plus(ttl));
    return clients;
  }
}
