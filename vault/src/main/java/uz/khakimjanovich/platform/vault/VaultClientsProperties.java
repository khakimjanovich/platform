package uz.khakimjanovich.platform.vault;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The client registry ({@link ClientRegistry}), for an app whose clients are added while it runs.
 * Neither has a default: without {@code path} there is no registry, and with it {@code cache-ttl}
 * must be set too.
 *
 * @param path the KV entry holding the clients, under {@code platform.vault.mount}
 * @param cacheTtl how long one read of it is used
 */
@ConfigurationProperties(VaultConstants.CLIENTS_PREFIX)
public record VaultClientsProperties(@Nullable String path, @Nullable Duration cacheTtl) {}
