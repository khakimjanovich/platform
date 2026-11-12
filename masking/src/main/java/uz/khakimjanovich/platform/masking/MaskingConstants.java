package uz.khakimjanovich.platform.masking;

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
}
