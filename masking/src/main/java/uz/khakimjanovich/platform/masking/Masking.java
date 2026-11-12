package uz.khakimjanovich.platform.masking;

import static uz.khakimjanovich.platform.masking.MaskingConstants.DIGITS;
import static uz.khakimjanovich.platform.masking.MaskingConstants.HIDDEN;
import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_COLON;
import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_EQUALS;
import static uz.khakimjanovich.platform.masking.MaskingConstants.MAX_LENGTH;
import static uz.khakimjanovich.platform.masking.MaskingConstants.XML_ELEMENT;
import static uz.khakimjanovich.platform.masking.MaskingConstants.XML_ITEM;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Masking {

  private final MaskingRules rules;

  public Masking(MaskingRules rules) {
    this.rules = rules;
  }

  public static Masking defaults() {
    return new Masking(MaskingRules.defaults());
  }

  public String body(byte @Nullable [] body, @Nullable String contentType) {
    if (body == null || body.length == 0) return "";
    var text = new String(body, StandardCharsets.UTF_8);
    var subtype = subtype(contentType);
    String masked;
    if (subtype.contains("json")) masked = json(text);
    else if (subtype.contains("xml")) masked = xml(text);
    else if (subtype.equals("x-www-form-urlencoded")) masked = form(text);
    else
      return "<" + body.length + " bytes" + (contentType == null ? "" : ", " + contentType) + ">";
    return cut(cards(masked));
  }

  public @Nullable String text(@Nullable String text) {
    if (text == null || text.isEmpty()) return text;
    return cards(pairs(KEY_COLON, pairs(KEY_EQUALS, text)));
  }

  public String uri(URI uri) {
    var query = uri.getRawQuery();
    var whole = uri.toString();
    if (query == null) return cards(whole);
    var at = whole.indexOf('?');
    return cards(
        whole.substring(0, at + 1) + form(query) + whole.substring(at + 1 + query.length()));
  }

  private String json(String text) {
    try {
      var node = JsonMapper.shared().readTree(text);
      mask(node, "");
      return node.toString();
    } catch (RuntimeException e) {
      return "<" + text.length() + " characters, not JSON>";
    }
  }

  private void mask(JsonNode node, String parent) {
    if (node instanceof ObjectNode object) {
      var names = object.propertyNames().stream().toList();
      for (var name : names) {
        var value = object.get(name);
        if (!value.isValueNode()) {
          mask(value, name);
          continue;
        }
        var key = name.toLowerCase(Locale.ROOT);
        if (rules.secretFields().contains(key) || secretHere(key, names, parent)) {
          object.put(name, HIDDEN);
        } else if (rules.tokenFields().contains(key)) object.put(name, last4(value.asString()));
        else if (rules.cardFields().contains(key)) object.put(name, card(value.asString()));
      }
    } else if (node.isArray()) {
      node.forEach(element -> mask(element, parent));
    }
  }

  private boolean secretHere(String key, List<String> siblings, String parent) {
    var context = rules.secretsBeside().get(key);
    if (context == null) return false;
    return context.contains(parent.toLowerCase(Locale.ROOT))
        || siblings.stream().anyMatch(name -> context.contains(name.toLowerCase(Locale.ROOT)));
  }

  private String xml(String text) {
    var items =
        replace(XML_ITEM, text, m -> m.group(1) + valueFor(m.group(2), m.group(3)) + m.group(4));
    return replace(
        XML_ELEMENT, items, m -> m.group(1) + valueFor(m.group(2), m.group(3)) + m.group(4));
  }

  private String form(String text) {
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

  private String valueFor(String name, String value) {
    var key = name.toLowerCase(Locale.ROOT);
    if (rules.secretFields().contains(key) || key.equals("code")) return HIDDEN;
    if (rules.tokenFields().contains(key)) return last4(value);
    if (rules.cardFields().contains(key)) return card(value);
    return value;
  }

  private String pairs(Pattern pattern, String text) {
    return replace(
        pattern,
        text,
        m -> m.group(1) + m.group(2) + m.group(3) + valueFor(m.group(1), m.group(4)) + m.group(3));
  }

  private static String cards(String text) {
    return replace(DIGITS, text, match -> card(match.group()));
  }

  private static String replace(
      Pattern pattern, String text, Function<java.util.regex.MatchResult, String> replacement) {
    return pattern
        .matcher(text)
        .replaceAll(match -> Matcher.quoteReplacement(replacement.apply(match)));
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

  private static String cut(String masked) {
    return masked.length() > MAX_LENGTH ? masked.substring(0, MAX_LENGTH) + "…" : masked;
  }

  private static String subtype(@Nullable String contentType) {
    if (contentType == null) return "";
    var type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return type.substring(type.indexOf('/') + 1);
  }
}
