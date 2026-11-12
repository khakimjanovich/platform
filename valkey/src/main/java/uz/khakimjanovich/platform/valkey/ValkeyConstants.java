package uz.khakimjanovich.platform.valkey;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ValkeyConstants {

  public static final String PROPERTIES_PREFIX = "platform.valkey";

  /** {@code <service>:<kind>:<id>}: the ACL user reaches only {@code <service>:*}. */
  public static final String KEY_FORMAT = "%s:%s:%s";

  /** A service or a kind: lower case words joined by {@code -}, never a {@code :} of its own. */
  public static final String NAME_REGEX = "[a-z0-9]+(-[a-z0-9]+)*";

  static final Pattern NAME = Pattern.compile(NAME_REGEX);

  public static final String LOG_UNAVAILABLE = "Valkey unavailable: {}";
}
