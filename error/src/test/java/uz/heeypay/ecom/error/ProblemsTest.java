package uz.heeypay.ecom.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Another service's error read as it came: a problem, or nothing anyone decided. */
class ProblemsTest {

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final RestClient client = builder.build();

  private RestClientResponseException answer(HttpStatus status, MediaType type, String body) {
    var response = withStatus(status).contentType(type);
    server.expect(requestTo("/pay")).andRespond(body == null ? response : response.body(body));
    return catchThrowableOfType(
        RestClientResponseException.class,
        () -> client.post().uri("/pay").retrieve().toBodilessEntity());
  }

  @Test
  void aProblemWithSubErrors() {
    var e =
        answer(
            HttpStatus.UNPROCESSABLE_CONTENT,
            MediaType.APPLICATION_PROBLEM_JSON,
            """
            {"type":"about:blank","title":"sv-gate-error","status":422,"code":"SV_GATE_ERROR",\
            "subErrors":[{"code":"-240","message":"Insufficient funds!"}]}""");
    var problem = Problems.of(e).orElseThrow();
    assertThat(problem.getStatus()).isEqualTo(422);
    assertThat(Problems.code(problem)).isEqualTo("SV_GATE_ERROR");
    assertThat(Problems.subErrors(problem))
        .containsExactly(new GeneralException.SubError("-240", "Insufficient funds!"));
  }

  @Test
  void aProblemWithoutSubErrors() {
    var e =
        answer(
            HttpStatus.NOT_FOUND,
            MediaType.APPLICATION_PROBLEM_JSON,
            "{\"status\":404,\"code\":\"NOT_FOUND\"}");
    var problem = Problems.of(e).orElseThrow();
    assertThat(Problems.code(problem)).isEqualTo("NOT_FOUND");
    assertThat(Problems.subErrors(problem)).isEmpty();
  }

  @Test
  void anHtmlPageIsNoProblem() {
    var e = answer(HttpStatus.BAD_GATEWAY, MediaType.TEXT_HTML, "<html>502 Bad Gateway</html>");
    assertThat(Problems.of(e)).isEmpty();
  }

  @Test
  void anEmptyBodyIsNoProblem() {
    assertThat(
            Problems.of(answer(HttpStatus.SERVICE_UNAVAILABLE, MediaType.APPLICATION_JSON, null)))
        .isEmpty();
  }

  @Test
  void aProblemWithoutACodeHasNone() {
    var e = answer(HttpStatus.BAD_REQUEST, MediaType.APPLICATION_PROBLEM_JSON, "{\"status\":400}");
    assertThat(Problems.code(Problems.of(e).orElseThrow())).isNull();
  }
}
