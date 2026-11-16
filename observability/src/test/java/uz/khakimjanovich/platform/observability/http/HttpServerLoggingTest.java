package uz.khakimjanovich.platform.observability.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uz.khakimjanovich.platform.masking.Masking;
import uz.khakimjanovich.platform.masking.MaskingRules;

@ExtendWith(OutputCaptureExtension.class)
class HttpServerLoggingTest {

  @RestController
  static class TestController {

    @PostMapping(
        value = "/payments",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    String pay(@RequestBody String body) {
      return body;
    }

    @GetMapping("/actuator/health")
    String health() {
      return "{\"status\":\"UP\"}";
    }

    @PostMapping(value = "/crypto/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    String crypto(@RequestBody String body) {
      return "{\"key\":\"super-secret\"}";
    }

    @PostMapping(value = "/file", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] file(@RequestBody byte[] body) {
      return body;
    }
  }

  private static MockMvc mvc(HttpLoggingProperties properties) {
    var masking = new Masking(MaskingRules.of(List.of("pan", "cvv", "authorization", "secret")));
    var filter = new HttpServerLoggingFilter(masking, properties);
    return MockMvcBuilders.standaloneSetup(new TestController()).addFilters(filter).build();
  }

  @Test
  void inboundAndOutboundBodiesAreMasked(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(true, 4000, List.of(), List.of("/actuator/**"), List.of());
    var mvc = mvc(properties);
    var payload = "{\"pan\":\"8600301234567890\",\"cvv\":\"123\"}";

    mvc.perform(
            post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(content().string(payload));

    assertThat(output)
        .contains("→ POST /payments | {\"pan\":\"860030......7890\",\"cvv\":\"...\"}")
        .contains("← 200 POST /payments")
        .doesNotContain("8600301234567890");
  }

  @Test
  void whitelistedAndSensitiveHeadersAreMasked(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(
            true,
            4000,
            List.of("content-type", "authorization"),
            List.of("/actuator/**"),
            List.of());
    var mvc = mvc(properties);

    mvc.perform(
            post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.x.abcd1234")
                .header("User-Agent", "curl/8.4")
                .content("{\"pan\":\"8600301234567890\"}"))
        .andExpect(status().isOk());

    assertThat(output)
        .contains(
            "[Content-Type: application/json, Authorization: Bearer............................1234]")
        .doesNotContain("abcd1234")
        .doesNotContain("curl/8.4");
  }

  @Test
  void excludedPathsEmitNoLogs(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(true, 4000, List.of(), List.of("/actuator/**"), List.of());
    var mvc = mvc(properties);

    mvc.perform(get("/actuator/health")).andExpect(status().isOk());

    assertThat(output).doesNotContain("/actuator/health");
  }

  @Test
  void binaryPayloadsAreLoggedBySize(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(true, 4000, List.of(), List.of("/actuator/**"), List.of());
    var mvc = mvc(properties);
    var bytes = new byte[] {1, 2, 3, 4, 5};

    mvc.perform(
            post("/file")
                .contentType(MediaType.APPLICATION_PDF)
                .accept(MediaType.APPLICATION_PDF)
                .content(bytes))
        .andExpect(status().isOk());

    assertThat(output).contains("<5 bytes, application/pdf>");
  }

  @Test
  void largePayloadsAreCappedAtMaxBodyBytes(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(true, 50, List.of(), List.of("/actuator/**"), List.of());
    var mvc = mvc(properties);
    var large = "{\"data\":\"" + "a".repeat(100) + "\"}";

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(large))
        .andExpect(status().isOk());

    assertThat(output).contains("<more than 50 bytes, application/json>");
  }

  @Test
  void noBodyPathsAreNotLogged(CapturedOutput output) throws Exception {
    var properties =
        new HttpLoggingProperties(
            true, 4000, List.of(), List.of("/actuator/**"), List.of("/crypto/**"));
    var mvc = mvc(properties);

    mvc.perform(
            post("/crypto/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seed\":\"secret123\"}"))
        .andExpect(status().isOk());

    assertThat(output)
        .contains("→ POST /crypto/keys | <not logged>")
        .contains("← 200 POST /crypto/keys")
        .doesNotContain("super-secret")
        .doesNotContain("secret123");
  }
}
