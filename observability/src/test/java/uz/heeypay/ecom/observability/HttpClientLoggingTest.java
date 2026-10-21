package uz.heeypay.ecom.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.heeypay.ecom.masking.Masking;

/** Client calls are logged masked, and only small readable answers are buffered for the log. */
@ExtendWith(OutputCaptureExtension.class)
class HttpClientLoggingTest {

  private final RestClient.Builder builder =
      RestClient.builder().requestInterceptor(new HttpClientLogging(Masking.defaults()));
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final RestClient client = builder.build();

  @Test
  void theQueryIsMasked(CapturedOutput output) {
    server
        .expect(requestTo("/users?username=aziz&access_token=eyJhbGci.x.abcd1234"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    client
        .get()
        .uri("/users?username=aziz&access_token=eyJhbGci.x.abcd1234")
        .retrieve()
        .body(String.class);
    assertThat(output)
        .contains("GET /users?username=aziz&access_token=…1234")
        .doesNotContain("eyJhbGci");
  }

  @Test
  void aSmallJsonAnswerIsLoggedMasked(CapturedOutput output) {
    server
        .expect(requestTo("/cards"))
        .andRespond(withSuccess("{\"pan\":\"8600301234567890\"}", MediaType.APPLICATION_JSON));
    var body = client.get().uri("/cards").retrieve().body(String.class);
    assertThat(body).isEqualTo("{\"pan\":\"8600301234567890\"}");
    assertThat(output).contains("860030******7890").doesNotContain("8600301234567890");
  }

  @Test
  void aLongAnswerIsNotBufferedButReachesTheCallerWhole(CapturedOutput output) {
    var big = "[" + "1,".repeat(3_000) + "1]";
    server.expect(requestTo("/report")).andRespond(withSuccess(big, MediaType.APPLICATION_JSON));
    assertThat(client.get().uri("/report").retrieve().body(String.class)).isEqualTo(big);
    assertThat(output).contains("<more than 4000 bytes, application/json>");
  }

  @Test
  void anUnreadableAnswerIsLoggedByItsSize(CapturedOutput output) {
    var pdf = new byte[] {'%', 'P', 'D', 'F', '-', '1'};
    server
        .expect(requestTo("/file"))
        .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF).header("Content-Length", "6"));
    assertThat(client.get().uri("/file").retrieve().body(byte[].class)).isEqualTo(pdf);
    assertThat(output).contains("<6 bytes, application/pdf>");
  }

  @Test
  void aClientSecretAnswerIsLoggedMasked(CapturedOutput output) {
    server
        .expect(requestTo("/clients/c-1/client-secret"))
        .andRespond(
            withSuccess("{\"type\":\"secret\",\"value\":\"abc\"}", MediaType.APPLICATION_JSON));
    client.get().uri("/clients/c-1/client-secret").retrieve().body(String.class);
    assertThat(output).contains("\"value\":\"***\"").doesNotContain("abc");
  }

  @Test
  void aCallMarkedNoBodiesLogsNeitherBody(CapturedOutput output) {
    server
        .expect(requestTo("/clients/c-1/client-secret"))
        .andRespond(withSuccess("{\"value\":\"abc\"}", MediaType.APPLICATION_JSON));
    var body =
        client
            .post()
            .uri("/clients/c-1/client-secret")
            .attribute(HttpClientLogging.NO_BODIES, true)
            .body("{\"name\":\"xyz\"}")
            .retrieve()
            .body(String.class);
    assertThat(body).isEqualTo("{\"value\":\"abc\"}");
    assertThat(output)
        .contains("→ POST /clients/c-1/client-secret | <not logged>")
        .contains("← 200 POST /clients/c-1/client-secret")
        .doesNotContain("abc", "xyz");
  }
}
