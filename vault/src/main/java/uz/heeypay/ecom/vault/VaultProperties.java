package uz.heeypay.ecom.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where Vault is, the token to use, and the KV version 2 mount. */
@ConfigurationProperties(VaultConstantUtils.PROPERTIES_PREFIX)
public record VaultProperties(String url, String token, @DefaultValue("secret") String mount) {}
