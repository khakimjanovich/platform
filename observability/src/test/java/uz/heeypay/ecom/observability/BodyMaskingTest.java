package uz.heeypay.ecom.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** What must never reach a log, in every format the services send. */
class BodyMaskingTest {

  private static String masked(String body, MediaType type) {
    return BodyMasking.masked(body.getBytes(StandardCharsets.UTF_8), type);
  }

  @Test
  void uzcardJsonKeepsItsErrorsButNotTheCard() {
    var request =
        """
        {"method":"cards.new.verify","params":{"card":{"pan":"8600301234567890","expiry":"2812"},\
        "otp":{"id":"otp-1","code":"123456"}},"error":{"code":-207,"message":"duplicate"}}""";
    assertThat(masked(request, MediaType.APPLICATION_JSON))
        .contains("860030******7890", "\"expiry\":\"***\"", "\"code\":\"***\"", "otp-1")
        .contains("\"code\":-207", "duplicate")
        .doesNotContain("8600301234567890", "2812", "123456");
  }

  @Test
  void theCardholderAndTokensAreHidden() {
    var response =
        """
        {"result":{"id":"9C51","pan":"561468******7502","fullName":"JENNIFER GREEN",\
        "phone":"998941905545","aacct":"0115022618000000000000100","token":"uztok-abcdef123"}}""";
    assertThat(masked(response, MediaType.APPLICATION_JSON))
        .contains("\"fullName\":\"***\"", "…5545", "…0100", "…f123")
        .doesNotContain("JENNIFER", "998941905545", "uztok-abcdef");
  }

  @Test
  void humoXmlItemsAndAMiddlewareCodeNextToAPan() {
    var soap =
        "<ns:Payment><item><name>pan</name><value>9860090101191238</value></item>"
            + "<item><name>expiry</name><value>2905</value></item></ns:Payment>";
    assertThat(masked(soap, MediaType.TEXT_XML))
        .contains("986009******1238", "<value>***</value>")
        .doesNotContain("9860090101191238", "2905");
    assertThat(
            masked(
                "{\"pan\":\"9860090101191238\",\"code\":\"660130\"}", MediaType.APPLICATION_JSON))
        .doesNotContain("660130");
  }

  @Test
  void keycloakFormsAndCredentialsAndAnyStrayCardNumber() {
    assertThat(
            masked(
                "grant_type=client_credentials&client_id=ecom&client_secret=s3cr3t",
                MediaType.APPLICATION_FORM_URLENCODED))
        .isEqualTo("grant_type=client_credentials&client_id=ecom&client_secret=***");
    assertThat(
            masked(
                "{\"credentials\":[{\"type\":\"password\",\"value\":\"hunter2\"}],"
                    + "\"note\":\"card 8600301234567890\"}",
                MediaType.APPLICATION_JSON))
        .doesNotContain("hunter2", "8600301234567890");
    assertThat(masked("<html>502</html>", MediaType.TEXT_HTML)).isEqualTo("<16 bytes, text/html>");
  }
}
