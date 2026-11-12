package uz.khakimjanovich.platform.vault;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Vault Transit ({@link VaultTransit}), for an app that seals values. Bound only when {@code mount}
 * is set, and then a blank one stops startup; the key names are the module's own settings.
 *
 * @param mount the Transit secrets engine's mount
 */
@Validated
@ConfigurationProperties(VaultConstants.TRANSIT_PREFIX)
public record VaultTransitProperties(@NotBlank String mount) {}
