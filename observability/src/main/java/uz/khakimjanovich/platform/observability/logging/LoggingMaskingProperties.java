package uz.khakimjanovich.platform.observability.logging;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for automated log masking under platform.logging.masking.
 *
 * @param maskChar the character used for masking
 * @param fields list of sensitive field names to mask (case-insensitive)
 */
@ConfigurationProperties("platform.logging.masking")
public record LoggingMaskingProperties(@Nullable String maskChar, @Nullable List<String> fields) {}
