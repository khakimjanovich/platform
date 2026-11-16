package uz.khakimjanovich.platform.observability;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for automated log masking under platform.logging.masking.
 *
 * @param maskChar the character used for masking, defaults to a dot
 * @param fields list of sensitive field names to mask (case-insensitive)
 */
@ConfigurationProperties("platform.logging.masking")
public record LoggingMaskingProperties(
    @DefaultValue(".") String maskChar, @DefaultValue List<String> fields) {}
