package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/** A public route reads no token; any other route reads the bearer token. */
class PublicRouteTokenResolverTest {

  private final PublicRouteTokenResolver resolver =
      new PublicRouteTokenResolver(
          new OrRequestMatcher(
              SecurityAutoConfiguration.route("/api/v1/pay/**"),
              SecurityAutoConfiguration.route("POST /api/v1/registrations/**")));

  private static MockHttpServletRequest request(String method, String path) {
    var request = new MockHttpServletRequest(method, path);
    request.addHeader("Authorization", "Bearer stale.token.value");
    return request;
  }

  @Test
  void aPublicRouteReadsNoToken() {
    assertThat(resolver.resolve(request("GET", "/api/v1/pay/x"))).isNull();
    assertThat(resolver.resolve(request("POST", "/api/v1/registrations/new"))).isNull();
  }

  @Test
  void anyOtherRouteReadsTheBearerToken() {
    assertThat(resolver.resolve(request("GET", "/api/v1/admin/partners")))
        .isEqualTo("stale.token.value");
    assertThat(resolver.resolve(request("GET", "/api/v1/registrations/new")))
        .as("public only for POST")
        .isEqualTo("stale.token.value");
  }
}
