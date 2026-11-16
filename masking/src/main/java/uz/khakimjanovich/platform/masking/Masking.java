package uz.khakimjanovich.platform.masking;

import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_COLON;
import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_EQUALS;
import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_PATH;
import static uz.khakimjanovich.platform.masking.MaskingConstants.KEY_SPACE;
import static uz.khakimjanovich.platform.masking.MaskingConstants.MAX_LENGTH;
import static uz.khakimjanovich.platform.masking.MaskingConstants.XML_ELEMENT;
import static uz.khakimjanovich.platform.masking.MaskingConstants.XML_ITEM;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

  public static Masking empty() {
    return new Masking(MaskingRules.empty());
  }

  public static Masking defaults() {
    return empty();
  }

  public boolean isEnabled() {
    return !rules.isEmpty();
  }

  public String maskValue(String value) {
    var trimmed = value.strip();
    if (trimmed.length() < 10) {
      return rules.maskChar().repeat(trimmed.length());
    }
    var middleDots = rules.maskChar().repeat(trimmed.length() - 10);
    return trimmed.substring(0, 6) + middleDots + trimmed.substring(trimmed.length() - 4);
  }

  public String body(byte @Nullable [] body, @Nullable String contentType) {
    if (body == null || body.length == 0) return "";
    var text = new String(body, StandardCharsets.UTF_8);
    if (rules.isEmpty()) return text;
    var subtype = subtype(contentType);
    String masked;
    if (subtype.contains("json")) masked = json(text);
    else if (subtype.contains("xml")) masked = xml(text);
    else if (subtype.equals("x-www-form-urlencoded")) masked = form(text);
    else
      return "<" + body.length + " bytes" + (contentType == null ? "" : ", " + contentType) + ">";
    return cut(masked);
  }

  public @Nullable String text(@Nullable String text) {
    if (text == null || text.isEmpty() || rules.isEmpty()) return text;
    return paths(pairs(KEY_SPACE, pairs(KEY_COLON, pairs(KEY_EQUALS, text))));
  }

  public String uri(URI uri) {
    var whole = uri.toString();
    if (rules.isEmpty()) return whole;
    var query = uri.getRawQuery();
    if (query == null) return paths(whole);
    var at = whole.indexOf('?');
    var pathPart = paths(whole.substring(0, at + 1));
    return pathPart + form(query) + whole.substring(at + 1 + query.length());
  }

  private String json(String text) {
    try {
      var node = JsonMapper.shared().readTree(text);
      mask(node);
      return node.toString();
    } catch (RuntimeException e) {
      return "<" + text.length() + " characters, not JSON>";
    }
  }

  private void mask(JsonNode node) {
    if (node instanceof ObjectNode object) {
      var names = object.propertyNames().stream().toList();
      for (var name : names) {
        var value = object.get(name);
        if (!value.isValueNode()) {
          mask(value);
          continue;
        }
        if (rules.contains(name)) {
          object.put(name, maskValue(value.asString()));
        }
      }
    } else if (node.isArray()) {
      node.forEach(this::mask);
    }
  }

  private String xml(String text) {
    var items =
        replace(
            XML_ITEM,
            text,
            m ->
                rules.contains(m.group(2))
                    ? m.group(1) + maskValue(m.group(3)) + m.group(4)
                    : m.group(0));
    return replace(
        XML_ELEMENT,
        items,
        m ->
            rules.contains(m.group(2))
                ? m.group(1) + maskValue(m.group(3)) + m.group(4)
                : m.group(0));
  }

  private String form(String text) {
    return Pattern.compile("&")
        .splitAsStream(text)
        .map(
            pair -> {
              var at = pair.indexOf('=');
              if (at < 0) return pair;
              var name = URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8);
              if (rules.contains(name)) {
                return pair.substring(0, at + 1) + maskValue(pair.substring(at + 1));
              }
              return pair;
            })
        .collect(Collectors.joining("&"));
  }

  private String pairs(Pattern pattern, String text) {
    return replace(
        pattern,
        text,
        m -> {
          var name = m.group(1);
          if (rules.contains(name)) {
            return m.group(1) + m.group(2) + m.group(3) + maskValue(m.group(4)) + m.group(3);
          }
          return m.group(0);
        });
  }

  private String paths(String text) {
    return replace(
        KEY_PATH,
        text,
        m -> {
          var name = m.group(1);
          if (rules.contains(name)) {
            return "/" + m.group(1) + "/" + maskValue(m.group(2));
          }
          return m.group(0);
        });
  }

  private static String replace(
      Pattern pattern, String text, Function<java.util.regex.MatchResult, String> replacement) {
    return pattern
        .matcher(text)
        .replaceAll(match -> Matcher.quoteReplacement(replacement.apply(match)));
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
