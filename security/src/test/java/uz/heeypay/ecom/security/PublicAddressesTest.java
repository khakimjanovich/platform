package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Merchant URLs reach only public addresses (FR-CHK-063). */
class PublicAddressesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "10.0.0.5",
        "172.16.0.1",
        "192.168.1.1",
        "127.0.0.1",
        "169.254.169.254",
        "100.64.0.1",
        "0.0.0.0",
        "::1",
        "fe80::1",
        "fd00::1",
        "::ffff:10.0.0.5",
        "no-such-host.invalid"
      })
  void privateLoopbackAndLinkLocalAreRefused(String host) {
    assertThat(PublicAddresses.resolvesPublic(host)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.8.8.8", "195.158.1.1", "2001:4860:4860::8888"})
  void publicAddressesPass(String host) {
    assertThat(PublicAddresses.resolvesPublic(host)).isTrue();
  }
}
