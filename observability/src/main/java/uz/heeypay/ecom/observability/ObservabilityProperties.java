package uz.heeypay.ecom.observability;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What this service adds to the names every service masks in its logs.
 *
 * @param masking field names to mask beyond the defaults
 */
@ConfigurationProperties("ecom.observability")
public record ObservabilityProperties(@DefaultValue Masking masking) {

  /**
   * Field names added to the defaults, compared ignoring case; the defaults are never repeated.
   *
   * @param additionalSecretFields hidden whole, e.g. a scheme's embossed name
   * @param additionalTokenFields cut to their last 4 characters, e.g. a scheme's card hash
   * @param additionalCardFields card numbers, kept to their first 6 and last 4 digits
   */
  public record Masking(
      @DefaultValue List<String> additionalSecretFields,
      @DefaultValue List<String> additionalTokenFields,
      @DefaultValue List<String> additionalCardFields) {}
}
