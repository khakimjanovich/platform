package uz.khakimjanovich.platform.observability;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What this service adds to the names every service masks in its logs.
 *
 * @param masking field names to mask beyond the defaults
 */
@ConfigurationProperties("platform.observability")
public record ObservabilityProperties(@DefaultValue Masking masking) {

  /**
   * Field names added to the defaults, compared ignoring case; the defaults are never repeated.
   *
   * @param additionalSecretFields hidden whole, e.g. a scheme's embossed name
   * @param additionalTokenFields cut to their last 4 characters, e.g. a scheme's card hash
   * @param additionalCardFields card numbers, kept to their first 6 and last 4 digits
   * @param additionalSecretsBeside in JSON, a field hidden only beside or under one of the given
   *     names, e.g. a scheme's {@code code: [pan, otp]}
   */
  public record Masking(
      @DefaultValue List<String> additionalSecretFields,
      @DefaultValue List<String> additionalTokenFields,
      @DefaultValue List<String> additionalCardFields,
      @DefaultValue Map<String, List<String>> additionalSecretsBeside) {}
}
