package uz.khakimjanovich.platform.observability;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for automated log masking under platform.logging.masking.
 *
 * @param enabled whether log masking is active across all sinks
 * @param cardPan whether to mask card PANs using PCI-DSS 4.0 layout (6+*+4)
 * @param cvv whether to mask CVV and CVC security codes
 * @param bearerToken whether to mask Bearer and JWT authorization tokens
 * @param phoneNumber whether to mask customer phone numbers
 * @param pinfl whether to mask personal identification numbers (PINFL and JShShIR)
 * @param additionalSecretFields extra field names to hide completely with asterisks
 * @param additionalTokenFields extra field names to truncate to their last 4 characters
 * @param additionalCardFields extra field names to format as masked card numbers
 * @param additionalSecretsBeside contextual field names hidden when alongside specific parents
 */
@ConfigurationProperties("platform.logging.masking")
public record LoggingMaskingProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("true") boolean cardPan,
    @DefaultValue("true") boolean cvv,
    @DefaultValue("true") boolean bearerToken,
    @DefaultValue("true") boolean phoneNumber,
    @DefaultValue("true") boolean pinfl,
    @DefaultValue List<String> additionalSecretFields,
    @DefaultValue List<String> additionalTokenFields,
    @DefaultValue List<String> additionalCardFields,
    @DefaultValue Map<String, List<String>> additionalSecretsBeside) {}
