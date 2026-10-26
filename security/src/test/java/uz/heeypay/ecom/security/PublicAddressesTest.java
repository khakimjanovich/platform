package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;
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
        "192.0.0.8",
        "192.0.2.1",
        "198.18.0.1",
        "198.51.100.1",
        "203.0.113.1",
        "224.0.0.1",
        "240.0.0.1",
        "255.255.255.255",
        "::1",
        "::",
        "fe80::1",
        "fd00::1",
        "::ffff:10.0.0.5",
        "::10.0.0.5",
        "64:ff9b::a00:5",
        "2002:a00:5::",
        "2001:0:a00:5::",
        "2001:db8::1",
        "ff02::1",
        "no-such-host.invalid"
      })
  void privateReservedAndEmbeddedAddressesAreRefused(String host) {
    assertThat(PublicAddresses.resolvesPublic(host)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.8.8.8", "195.158.1.1", "2001:4860:4860::8888"})
  void publicAddressesPass(String host) {
    assertThat(PublicAddresses.resolvesPublic(host)).isTrue();
  }

  @Test
  void aUrlIsHttpsOnPort443ToAPublicAddress() {
    assertThat(PublicAddresses.allowed("https://8.8.8.8/hooks", List.of())).isTrue();
    assertThat(PublicAddresses.allowed("https://8.8.8.8:443/hooks", List.of())).isTrue();
    assertThat(PublicAddresses.allowed("https://8.8.8.8:6379/hooks", List.of())).isFalse();
    assertThat(PublicAddresses.allowed("https://10.0.0.5/hooks", List.of())).isFalse();
    assertThat(PublicAddresses.allowed("http://8.8.8.8/hooks", List.of())).as("http").isFalse();
    assertThat(PublicAddresses.allowed("ftp://8.8.8.8/hooks", List.of())).as("ftp").isFalse();
    assertThat(PublicAddresses.allowed("https://exa mple.com/x", List.of()))
        .as("malformed")
        .isFalse();
    assertThat(PublicAddresses.allowed("hooks/relative", List.of())).as("no host").isFalse();
  }

  @Test
  void aTestHostIsAnExactHostAndPortOverHttpOrHttps() {
    var sandbox = List.of("Processing-Sandbox:8080");
    assertThat(PublicAddresses.allowed("http://processing-sandbox:8080/merchant", sandbox))
        .isTrue();
    assertThat(PublicAddresses.allowed("http://processing-sandbox:5432/x", sandbox)).isFalse();
    assertThat(PublicAddresses.allowed("ftp://processing-sandbox:8080/x", sandbox)).isFalse();
    assertThat(PublicAddresses.allowed("http://localhost:80/x", List.of("localhost:80"))).isTrue();
  }

  @Test
  void theResolverHandsOutOnlyCheckedAddresses() throws Exception {
    assertThat(PublicAddresses.resolvePublic("8.8.8.8")).hasSize(1);
    assertThatThrownBy(() -> PublicAddresses.resolvePublic("169.254.169.254"))
        .isInstanceOf(UnknownHostException.class);
  }
}
