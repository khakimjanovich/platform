package uz.khakimjanovich.platform.vault;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vault Transit ({@link VaultTransit}), for an app that seals values. No default: without {@code
 * mount} there is no {@link VaultTransit}. The key names are the module's own.
 *
 * @param mount the Transit secrets engine's mount
 */
@ConfigurationProperties(VaultConstants.TRANSIT_PREFIX)
public record VaultTransitProperties(@Nullable String mount) {}
