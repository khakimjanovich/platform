package uz.khakimjanovich.platform.masking;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MaskingConstants {

  public static final String HIDDEN = "***";

  static final int MAX_LENGTH = 4_000;

  static final Pattern DIGITS = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");

  static final Pattern XML_ITEM =
      Pattern.compile("(<name>\\s*([\\w:]+)\\s*</name>\\s*<value>)([^<]*)(</value>)");

  static final Pattern XML_ELEMENT =
      Pattern.compile("(<(?:[\\w-]+:)?([\\w-]+)(?:\\s[^>]*)?>)([^<]*)(</)");

  static final Pattern KEY_EQUALS =
      Pattern.compile("(?<![\\w.-])([A-Za-z_][\\w.-]*)(=)(\"?)([^\\s&,;\"']*)\\3");

  static final Pattern KEY_COLON =
      Pattern.compile(
          "(?<![\\w.-])([A-Za-z_][\\w.-]*)(\"?\\s*:\\s*)(\"?)([^\\s&,;\"':]*+)\\3(?!:)");

  public static final Set<String> DEFAULT_SECRET_FIELDS =
      Set.of(
          "expiry",
          "cvv",
          "cvc",
          "password",
          "client_secret",
          "clientsecret",
          "secret",
          "fullname",
          "cardholder",
          "authorization",
          "bearer",
          "api_key",
          "apikey",
          "x-api-key");

  public static final Set<String> DEFAULT_TOKEN_FIELDS =
      Set.of(
          "token",
          "access_token",
          "refresh_token",
          "id_token",
          "phone",
          "account",
          "pinfl",
          "jshshir");

  public static final Set<String> DEFAULT_CARD_FIELDS = Set.of("pan", "cardnumber", "number");

  public static final Map<String, Set<String>> DEFAULT_SECRETS_BESIDE =
      Map.of("value", Set.of("type"));
}
