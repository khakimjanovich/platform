package uz.heeypay.ecom.crypto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keys as base64 (32 bytes each), by id. New data uses {@code activeKeyId}; older ids stay to read
 * older data until it is rewrapped. The blind index has its own key. All of them come from Vault.
 *
 * @param keys AES-256 keys as base64 (32 bytes), by key id
 * @param activeKeyId the key new values are sealed with; setting it turns the library on
 * @param blindIndexKey the HMAC-SHA256 key of the blind index, base64 (32 bytes)
 */
@Validated
@ConfigurationProperties(CryptoConstants.PROPERTIES_PREFIX)
public record CryptoProperties(
    @NotEmpty Map<String, String> keys,
    @NotBlank String activeKeyId,
    @NotBlank String blindIndexKey) {}
