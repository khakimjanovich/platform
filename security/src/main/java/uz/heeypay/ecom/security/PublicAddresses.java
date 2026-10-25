package uz.heeypay.ecom.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import lombok.experimental.UtilityClass;

/**
 * Whether a merchant's URL may be called: its host must resolve, and only to public addresses. A
 * private (10/8, 172.16/12, 192.168/16, fc00::/7), loopback, link-local, carrier-grade NAT
 * (100.64/10), "this network" (0/8), any-local or multicast address would let a merchant point the
 * bank at its own network (SSRF). Check again right before each call: DNS can change in between.
 */
@UtilityClass
public class PublicAddresses {

  /** False when the host does not resolve, or any of its addresses is not public. */
  public static boolean resolvesPublic(String host) {
    try {
      return Arrays.stream(InetAddress.getAllByName(host)).allMatch(PublicAddresses::isPublic);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  static boolean isPublic(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    var bytes = address.getAddress();
    if (bytes.length == 4) {
      var first = bytes[0] & 0xff;
      return first != 0 && !(first == 100 && (bytes[1] & 0xc0) == 64);
    }
    return (bytes[0] & 0xfe) != 0xfc; // IPv6 unique local
  }
}
