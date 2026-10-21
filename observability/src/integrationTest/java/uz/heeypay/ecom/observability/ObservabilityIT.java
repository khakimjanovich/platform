package uz.heeypay.ecom.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One request against Grafana's LGTM stack: its trace reaches Tempo and its log lines Loki under
 * the same trace id, which the response names in X-Request-Id; a card number in a log line and a
 * token in a client call's query arrive masked.
 */
@SpringBootTest(
    classes = ObservabilityIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.application.name=observability-it")
@Testcontainers
class ObservabilityIT {

  private static final String PAN = "8600301234567890";
  private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.x.abcd1234";

  @Container @ServiceConnection
  static final LgtmStackContainer LGTM =
      new LgtmStackContainer(ResourceBundle.getBundle("images").getString("lgtm"))
          .withStartupTimeout(Duration.ofMinutes(3));

  @RegisterExtension
  static final WireMockExtension SCHEME =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void scheme(DynamicPropertyRegistry registry) {
    registry.add("scheme.url", SCHEME::baseUrl);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {

    @RestController
    static class Payments {
      private static final Logger log = LoggerFactory.getLogger(Payments.class);
      private final RestClient scheme;

      Payments(RestClient.Builder builder, org.springframework.core.env.Environment environment) {
        this.scheme = builder.baseUrl(environment.getRequiredProperty("scheme.url")).build();
      }

      @GetMapping("/pay")
      String pay() {
        log.info("paid with card {}", PAN);
        log.error("declined", new IllegalStateException("card " + PAN));
        return scheme.get().uri("/cards?access_token=" + TOKEN).retrieve().body(String.class);
      }
    }
  }

  @LocalServerPort int port;

  @Test
  void aRequestsTraceAndMaskedLogsShareItsRequestId() {
    SCHEME.stubFor(
        get(urlPathEqualTo("/cards"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));
    var response =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/pay")
            .retrieve()
            .toEntity(String.class);
    var requestId = response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(requestId).matches("[0-9a-f]{32}");

    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(http(LGTM.getTempoUrl() + "/api/traces/" + requestId))
                    .contains("observability-it"));

    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var lines =
                  loki("{service_name=\"observability-it\"} | trace_id=\"" + requestId + "\"");
              assertThat(lines)
                  .contains("paid with card 860030******7890")
                  .contains("/cards?access_token=…1234")
                  .contains("IllegalStateException")
                  .doesNotContain(PAN, TOKEN);
            });
  }

  private static String loki(String query) {
    var start = Instant.now().minus(Duration.ofMinutes(10)).toEpochMilli() * 1_000_000;
    return http(
        LGTM.getLokiUrl()
            + "/loki/api/v1/query_range?limit=1000&start="
            + start
            + "&query="
            + URLEncoder.encode(query, StandardCharsets.UTF_8));
  }

  private static String http(String url) {
    return RestClient.create().get().uri(URI.create(url)).retrieve().body(String.class);
  }
}
