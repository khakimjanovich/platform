package uz.heeypay.ecom.error;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every error's definition by its type, from the services' {@code properties/*-exception.yaml}
 * files under {@code exception.starter.error}.
 */
@ConfigurationProperties(ErrorConstantUtils.PROPERTIES_PREFIX)
public record ErrorStarterProperties(Map<String, ErrorDefinition> error) {

  public ErrorStarterProperties {
    error = error == null ? Map.of() : error;
  }

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
