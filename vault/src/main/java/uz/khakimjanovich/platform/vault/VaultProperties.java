package uz.khakimjanovich.platform.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the library reads from Vault. Where Vault is and how the app signs in are Spring Cloud
 * Vault's own {@code spring.cloud.vault.*} (TOKEN locally, APPROLE or KUBERNETES elsewhere).
 *
 * @param mount the KV version 2 secrets engine's mount
 */
@ConfigurationProperties(VaultConstants.PROPERTIES_PREFIX)
public record VaultProperties(@DefaultValue(VaultConstants.DEFAULT_MOUNT) String mount) {}
