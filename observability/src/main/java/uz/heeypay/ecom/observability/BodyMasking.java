package uz.heeypay.ecom.observability;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * An HTTP body as it may be logged. Card numbers keep their first 6 and last 4 digits, tokens their
 * last 4 characters, and expiry dates, codes, passwords, secrets and keys are hidden, in JSON, XML
 * (also Humo's name/value items) and forms. As a last guard, any run of 13 to 19 digits is treated
 * as a card number. Anything else is shown only by its size.
 */
final class BodyMasking {

  static final String HIDDEN = "***";
  private static final int MAX_LENGTH = 4_000;

  /** Hidden whole. */
  private static final Set<String> SECRET =
      Set.of(
          "expiry",
          "cvv",
          "cvc",
          "password",
          "client_secret",
          "clientsecret",
          "secret",
          "signingkey",
          "otp_code",
          "fullname",
          "cardholder");

  /** Cut to the last 4 characters: tokens, and the cardholder's phone and bank account. */
  private static final Set<String> TOKEN =
      Set.of(
          "token",
          "cardtoken",
          "processortoken",
          "access_token",
          "refresh_token",
          "id_token",
          "phone",
          "aacct",
          "account");

  /** Card numbers: first 6 and last 4. */
  private static final Set<String> CARD = Set.of("pan", "cardnumber", "number");

  private static final Pattern DIGITS = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");
  private static final Pattern XML_ITEM =
      Pattern.compile("(<name>\\s*([\\w:]+)\\s*</name>\\s*<value>)([^<]*)(</value>)");
  private static final Pattern XML_ELEMENT =
      Pattern.compile("(<(?:[\\w-]+:)?([\\w-]+)(?:\\s[^>]*)?>)([^<]*)(</)");

  private BodyMasking() {}

  static String masked(byte[] body, MediaType type) {
    if (body == null || body.length == 0) return "";
    var text = new String(body, StandardCharsets.UTF_8);
    String masked;
    if (type != null && type.getSubtype().contains("json")) masked = json(text);
    else if (type != null && type.getSubtype().contains("xml")) masked = xml(text);
    else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(type)) masked = form(text);
    else return "<" + body.length + " bytes" + (type == null ? "" : ", " + type) + ">";
    masked = DIGITS.matcher(masked).replaceAll(match -> card(match.group()));
    return masked.length() > MAX_LENGTH ? masked.substring(0, MAX_LENGTH) + "…" : masked;
  }

  private static String json(String text) {
    try {
      var node = JsonMapper.shared().readTree(text);
      mask(node, "");
      return node.toString();
    } catch (RuntimeException e) {
      return "<" + text.length() + " characters, not JSON>";
    }
  }

  private static void mask(JsonNode node, String parent) {
    if (node instanceof ObjectNode object) {
      // A code next to a card number, or under "otp", is a one-time code; Keycloak's credentials
      // are {"type": "password", "value": …}.
      var oneTimeCode = parent.equals("otp") || object.has("pan");
      var credential = "password".equals(object.path("type").asString(""));
      for (var name : object.propertyNames().stream().toList()) {
        var value = object.get(name);
        if (!value.isValueNode()) {
          mask(value, name);
          continue;
        }
        var key = name.toLowerCase(Locale.ROOT);
        if (SECRET.contains(key)
            || (key.equals("code") && oneTimeCode)
            || (key.equals("value") && credential)) {
          object.put(name, HIDDEN);
        } else if (TOKEN.contains(key)) object.put(name, last4(value.asString()));
        else if (CARD.contains(key)) object.put(name, card(value.asString()));
      }
    } else if (node.isArray()) {
      node.forEach(element -> mask(element, parent));
    }
  }

  private static String xml(String text) {
    var items =
        XML_ITEM
            .matcher(text)
            .replaceAll(m -> m.group(1) + valueFor(m.group(2), m.group(3)) + m.group(4));
    return XML_ELEMENT
        .matcher(items)
        .replaceAll(m -> m.group(1) + valueFor(m.group(2), m.group(3)) + m.group(4));
  }

  private static String form(String text) {
    return Pattern.compile("&")
        .splitAsStream(text)
        .map(
            pair -> {
              var at = pair.indexOf('=');
              if (at < 0) return pair;
              var name = URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8);
              return pair.substring(0, at + 1) + valueFor(name, pair.substring(at + 1));
            })
        .collect(Collectors.joining("&"));
  }

  private static String valueFor(String name, String value) {
    var key = name.toLowerCase(Locale.ROOT);
    if (SECRET.contains(key) || key.equals("code")) return HIDDEN;
    if (TOKEN.contains(key)) return last4(value);
    if (CARD.contains(key)) return card(value);
    return value;
  }

  private static String card(String number) {
    var digits = number.strip();
    return digits.length() < 13
        ? HIDDEN
        : digits.substring(0, 6)
            + "*".repeat(digits.length() - 10)
            + digits.substring(digits.length() - 4);
  }

  private static String last4(String token) {
    return token.length() <= 4 ? HIDDEN : "…" + token.substring(token.length() - 4);
  }
}
