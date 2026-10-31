package uz.khakimjanovich.platform.security;

/**
 * A trusted issuer's clients that are known only while the app runs, by group (an issuer's {@code
 * client-group}): a registry the app reads, such as one in Vault, where a client is added or taken
 * away without a restart. The app provides the one bean.
 */
@FunctionalInterface
public interface ClientGroups {

  /**
   * Whether {@code client} is in {@code group} now. False when that can't be told (the registry
   * unreachable), so a token is refused rather than accepted on old or assumed data.
   */
  boolean contains(String group, String client);
}
