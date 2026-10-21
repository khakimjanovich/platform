package uz.heeypay.ecom.error;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every error's definition by its type, from the services' {@code properties/*-exception.yaml}
 * files under {@code exception.starter.error}.
 *
 * @param error every error's definition, by its type (the code clients see)
 */
@ConfigurationProperties(ErrorConstants.PROPERTIES_PREFIX)
public record ErrorStarterProperties(Map<String, ErrorDefinition> error) {

  public ErrorStarterProperties {
    error = error == null ? Map.of() : error;
  }

  /**
   * One error: its status, title, and message in each language; {0}, {1}… are its parameters.
   *
   * @param httpCode the HTTP status it answers with
   * @param title a short kebab-case title, the problem's {@code title}
   * @param isLoggable a 4xx worth a warning in the log (5xx are always errors)
   * @param messageEn the message in English, also used when a language is missing
   * @param messageRu the message in Russian
   * @param messageUz the message in Uzbek
   */
  public record ErrorDefinition(
      int httpCode,
      String title,
      boolean isLoggable,
      String messageEn,
      String messageRu,
      String messageUz) {

    /** The message in the locale's language: Russian, Uzbek, or English for everything else. */
    public String message(Locale locale, Object... params) {
      var message =
          switch (locale.getLanguage()) {
            case "ru" -> messageRu;
            case "uz" -> messageUz;
            default -> messageEn;
          };
      if (message == null) message = messageEn;
      for (var i = 0; i < params.length; i++) {
        message = message.replace("{" + i + "}", String.valueOf(params[i]));
      }
      return message;
    }
  }
}
