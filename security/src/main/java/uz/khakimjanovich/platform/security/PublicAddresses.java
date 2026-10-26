package uz.khakimjanovich.platform.security;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Whether a merchant's URL may be called. It must be https on the default port, and its host must
 * resolve, only to public addresses: a merchant must not point the bank at its own network (SSRF).
 * IPv4 must be outside every private, shared, loopback, link-local, reserved, documentation and
 * multicast range; IPv6 must be global unicast (2000::/3) and none of the forms that carry an IPv4
 * address inside (6to4, Teredo, NAT64) or are for documentation.
 *
 * <p>Checking the URL is not enough on its own: DNS can answer differently when the client connects
 * (rebinding). A client calling merchants resolves through {@link #resolvePublic}, so it connects
 * only to the addresses that were checked, and never follows redirects, which could point anywhere.
 */
@UtilityClass
public class PublicAddresses {

  private static final List<Range> REFUSED_V4 =
      List.of(
          Range.of("0.0.0.0/8"),
          Range.of("10.0.0.0/8"),
          Range.of("100.64.0.0/10"),
          Range.of("127.0.0.0/8"),
          Range.of("169.254.0.0/16"),
          Range.of("172.16.0.0/12"),
          Range.of("192.0.0.0/24"),
          Range.of("192.0.2.0/24"),
          Range.of("192.168.0.0/16"),
          Range.of("198.18.0.0/15"),
          Range.of("198.51.100.0/24"),
          Range.of("203.0.113.0/24"),
          Range.of("224.0.0.0/4"),
          Range.of("240.0.0.0/4"));

  private static final Range GLOBAL_V6 = Range.of("2000::/3");
  private static final String HTTPS = "https";
  private static final String HTTP = "http";
  private static final int HTTPS_PORT = 443;
  private static final int HTTP_PORT = 80;
  private static final String NOT_PUBLIC = "%s resolves to an address that is not public";

  private static final List<Range> REFUSED_V6 =
      List.of(
          Range.of("2001::/32"), // Teredo
          Range.of("2001:db8::/32"), // documentation
          Range.of("2002::/16"), // 6to4
          Range.of("64:ff9b::/96")); // NAT64

  /**
   * The URL is https on port 443 and its host resolves only to public addresses; or it is a listed
   * local stand-in: {@code testHosts} are exact {@code host:port} entries, over http or https. A
   * malformed URL is not allowed.
   */
  public static boolean allowed(String url, Collection<String> testHosts) {
    var uri = parse(url);
    if (uri == null) return false;
    if (allowedTestHost(url, testHosts)) return true;
    return HTTPS.equals(scheme(uri)) && port(uri) == HTTPS_PORT && resolvesPublic(uri.getHost());
  }

  /** The URL is http or https to one of {@code testHosts}, exact {@code host:port} entries. */
  public static boolean allowedTestHost(String url, Collection<String> testHosts) {
    var uri = parse(url);
    if (uri == null) return false;
    var entry = uri.getHost().toLowerCase(Locale.ROOT) + ":" + port(uri);
    return testHosts.stream().anyMatch(test -> test.toLowerCase(Locale.ROOT).equals(entry));
  }

  /** Whether some {@code testHosts} entry names this host, on any of its ports. */
  public static boolean isTestHost(String host, Collection<String> testHosts) {
    var prefix = host.toLowerCase(Locale.ROOT) + ":";
    return testHosts.stream().anyMatch(test -> test.toLowerCase(Locale.ROOT).startsWith(prefix));
  }

  /** An absolute http or https URL with a host, or null. */
  private static @Nullable URI parse(String url) {
    try {
      var uri = new URI(url);
      var scheme = scheme(uri);
      return uri.getHost() != null && (HTTPS.equals(scheme) || HTTP.equals(scheme)) ? uri : null;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private static String scheme(URI uri) {
    return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
  }

  private static int port(URI uri) {
    if (uri.getPort() != -1) return uri.getPort();
    return HTTPS.equals(scheme(uri)) ? HTTPS_PORT : HTTP_PORT;
  }

  /**
   * The host's addresses, when every one is public; for a client's DNS resolver, so the connection
   * goes only to addresses that were checked.
   *
   * @throws UnknownHostException when the host does not resolve or any address is not public
   */
  public static InetAddress[] resolvePublic(String host) throws UnknownHostException {
    var addresses = InetAddress.getAllByName(host);
    if (!Arrays.stream(addresses).allMatch(PublicAddresses::isPublic)) {
      throw new UnknownHostException(NOT_PUBLIC.formatted(host));
    }
    return addresses;
  }

  /** False when the host does not resolve, or any of its addresses is not public. */
  public static boolean resolvesPublic(String host) {
    try {
      resolvePublic(host);
      return true;
    } catch (UnknownHostException e) {
      return false;
    }
  }

  static boolean isPublic(InetAddress address) {
    var bytes = address.getAddress();
    if (bytes.length == 4) return REFUSED_V4.stream().noneMatch(range -> range.contains(bytes));
    return GLOBAL_V6.contains(bytes)
        && REFUSED_V6.stream().noneMatch(range -> range.contains(bytes));
  }

  /** A CIDR block. */
  private record Range(byte[] network, int prefix) {

    static Range of(String cidr) {
      var parts = cidr.split("/");
      try {
        return new Range(InetAddress.getByName(parts[0]).getAddress(), Integer.parseInt(parts[1]));
      } catch (UnknownHostException e) {
        throw new IllegalStateException(e);
      }
    }

    boolean contains(byte[] address) {
      if (address.length != network.length) return false;
      var bits = address.length * 8;
      var mask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).shiftRight(prefix).not();
      return new BigInteger(1, address).and(mask).equals(new BigInteger(1, network).and(mask));
    }
  }
}
