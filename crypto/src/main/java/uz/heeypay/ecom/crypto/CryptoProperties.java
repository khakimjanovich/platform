package uz.heeypay.ecom.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keys as base64 (32 bytes each), by id. New data uses {@code activeKeyId}; older ids stay to read
 * older data until it is rewrapped. The blind index has its own key.
 *
 * @param keys AES-256 keys as base64 (32 bytes), by key id
 * @param activeKeyId the key new values are sealed with; setting it turns the library on
 * @param blindIndexKey the HMAC-SHA256 key of the blind index, base64 (32 bytes)
 */
@ConfigurationProperties(CryptoConstants.PROPERTIES_PREFIX)
public record CryptoProperties(
    Map<String, String> keys, String activeKeyId, String blindIndexKey) {}
