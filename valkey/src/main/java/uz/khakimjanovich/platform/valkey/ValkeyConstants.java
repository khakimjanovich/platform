package uz.khakimjanovich.platform.valkey;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ValkeyConstants {

  public static final String PROPERTIES_PREFIX = "platform.valkey";

  public static final String KEY_FORMAT = "%s:%s:%s";

  public static final String NAME_REGEX = "[a-z0-9]+(-[a-z0-9]+)*";

  static final Pattern NAME = Pattern.compile(NAME_REGEX);

  public static final String LOG_UNAVAILABLE = "Valkey unavailable: {}";
}
