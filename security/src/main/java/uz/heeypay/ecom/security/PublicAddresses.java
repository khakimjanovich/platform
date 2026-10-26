package uz.heeypay.ecom.security;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Whether a merchant's URL may be called. Its host must resolve, and only to public addresses: a
 * merchant must not point the bank at its own network (SSRF). IPv4 must be outside every private,
 * shared, loopback, link-local, reserved, documentation and multicast range; IPv6 must be global
 * unicast (2000::/3) and none of the forms that carry an IPv4 address inside (6to4, Teredo, NAT64)
 * or are for documentation. Check again right before each call: DNS can change in between.
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

  private static final List<Range> REFUSED_V6 =
      List.of(
          Range.of("2001::/32"), // Teredo
          Range.of("2001:db8::/32"), // documentation
          Range.of("2002::/16"), // 6to4
          Range.of("64:ff9b::/96")); // NAT64

  /**
   * The URL's host is one of {@code testHosts} (local stand-ins, any port), or it uses the default
   * https port and resolves only to public addresses.
   */
  public static boolean allowed(String url, Collection<String> testHosts) {
    var uri = URI.create(url);
    var host = uri.getHost();
    if (host == null) return false;
    if (testHosts.contains(host.toLowerCase(Locale.ROOT))) return true;
    return uri.getPort() == -1 && resolvesPublic(host);
  }

  /** False when the host does not resolve, or any of its addresses is not public. */
  public static boolean resolvesPublic(String host) {
    try {
      return Arrays.stream(InetAddress.getAllByName(host)).allMatch(PublicAddresses::isPublic);
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
