package uz.heeypay.ecom.error;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Problems read off a real HTTP exchange, the way a module reads an adapter's answer. */
class ProblemsIT {

  @RegisterExtension
  static final WireMockExtension ADAPTER =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private RestClientResponseException call(String path) {
    return catchThrowableOfType(
        RestClientResponseException.class,
        () -> RestClient.create(ADAPTER.baseUrl()).post().uri(path).retrieve().toBodilessEntity());
  }

  @Test
  void a422ProblemIsReadWithItsSubErrors() {
    ADAPTER.stubFor(
        post("/payments")
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"title":"sv-gate-error","status":422,"code":"SV_GATE_ERROR",\
                        "subErrors":[{"code":"-240","message":"Insufficient funds!"}]}""")));
    var problem = Problems.of(call("/payments")).orElseThrow();
    assertThat(Problems.code(problem)).isEqualTo("SV_GATE_ERROR");
    assertThat(Problems.subErrors(problem)).extracting("code").containsExactly("-240");
  }

  @Test
  void a502HtmlPageIsNoProblem() {
    ADAPTER.stubFor(
        post("/payments")
            .willReturn(
                aResponse()
                    .withStatus(502)
                    .withHeader("Content-Type", "text/html")
                    .withBody("<html><body>502 Bad Gateway</body></html>")));
    var e = call("/payments");
    assertThat(e.getStatusCode().value()).isEqualTo(502);
    assertThat(Problems.of(e)).isEmpty();
  }
}
