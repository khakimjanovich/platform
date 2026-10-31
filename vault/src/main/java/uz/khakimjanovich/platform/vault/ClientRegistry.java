package uz.khakimjanovich.platform.vault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The app's clients as a registry in one Vault entry ({@code platform.vault.clients.path}): one key
 * per client id, its value the client's record, which this class passes on unread (its format is
 * the app's). Whoever runs Vault adds or takes away a client there while the app runs; the app only
 * reads it.
 *
 * <p>The whole entry is read once per {@code cache-ttl}. Past that, a failed read fails closed: no
 * client is known until Vault answers again, never an old copy. No entry at all is no client.
 */
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

  /**
   * The client's record, or empty for a client not there. Throws {@link VaultUnavailableException}
   * if Vault can't be reached once the last read is too old, so callers fail closed.
   */
  public Optional<String> find(String clientId) {
    return Optional.ofNullable(all().get(clientId));
  }

  /** Every client's record by its id. Throws as {@link #find} does. */
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
