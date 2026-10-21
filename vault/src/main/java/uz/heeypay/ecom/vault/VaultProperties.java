package uz.heeypay.ecom.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Vault is, the token to use, and the KV version 2 mount.
 *
 * @param url Vault's address, e.g. {@code http://localhost:8200}; setting it turns the library on
 * @param token the token the app reads with
 * @param mount the KV version 2 secrets engine's mount
 */
@ConfigurationProperties(VaultConstants.PROPERTIES_PREFIX)
public record VaultProperties(String url, String token, @DefaultValue("secret") String mount) {}
