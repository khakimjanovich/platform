package uz.khakimjanovich.platform.valkey;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * No default: a missing or unworkable value stops startup, naming the property.
 *
 * @param service the first part of every key; the app's Valkey ACL user reaches only keys {@code
 *     <service>:*}
 */
@Validated
@ConfigurationProperties(ValkeyConstants.PROPERTIES_PREFIX)
public record ValkeyProperties(
    @NotNull @Pattern(regexp = ValkeyConstants.NAME_REGEX) String service) {}
