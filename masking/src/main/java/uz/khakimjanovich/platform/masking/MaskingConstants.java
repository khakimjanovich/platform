package uz.khakimjanovich.platform.masking;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MaskingConstants {

  public static final String DEFAULT_MASK_CHAR = ".";

  static final int MAX_LENGTH = 4_000;

  static final Pattern XML_ITEM =
      Pattern.compile("(<name>\\s*([\\w:]+)\\s*</name>\\s*<value>)([^<]*)(</value>)");

  static final Pattern XML_ELEMENT =
      Pattern.compile("(<(?:[\\w-]+:)?([\\w-]+)(?:\\s[^>]*)?>)([^<]*)(</)");

  static final Pattern KEY_EQUALS =
      Pattern.compile("(?<![\\w.-])([A-Za-z_][\\w.-]*)(=)(\"?)([^\\s&,;\"']*)\\3");

  static final Pattern KEY_COLON =
      Pattern.compile(
          "(?<![\\w.-])([A-Za-z_][\\w.-]*)(\"?\\s*:\\s*)(\"?)([^\\s&,;\"':]*+)\\3(?!:)");

  static final Pattern KEY_SPACE =
      Pattern.compile("(?<![\\w.-])([A-Za-z_][\\w.-]*)(\\s+)(\"?)([^\\s&,;\"':]+)\\3");

  static final Pattern KEY_PATH = Pattern.compile("/([A-Za-z_][\\w.-]*)/([^/?#\\s,;\"']+)");
}
