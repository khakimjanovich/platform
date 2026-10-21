package uz.heeypay.ecom.masking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What must never reach a log, in every format the services send. */
class MaskingTest {

  private static final String JSON = "application/json";
  private static final Masking MASKING = Masking.defaults();

  private static String masked(String body, String type) {
    return MASKING.body(body.getBytes(StandardCharsets.UTF_8), type);
  }

  @Test
  void uzcardJsonKeepsItsErrorsButNotTheCard() {
    var request =
        """
        {"method":"cards.new.verify","params":{"card":{"pan":"8600301234567890","expiry":"2812"},\
        "otp":{"id":"otp-1","code":"123456"}},"error":{"code":-207,"message":"duplicate"}}""";
    assertThat(masked(request, JSON))
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
    assertThat(masked(response, JSON))
        .contains("\"fullName\":\"***\"", "…5545", "…0100", "…f123")
        .doesNotContain("JENNIFER", "998941905545", "uztok-abcdef");
  }

  @Test
  void humoXmlItemsAndAMiddlewareCodeNextToAPan() {
    var soap =
        "<ns:Payment><item><name>pan</name><value>9860090101191238</value></item>"
            + "<item><name>expiry</name><value>2905</value></item></ns:Payment>";
    assertThat(masked(soap, "text/xml"))
        .contains("986009******1238", "<value>***</value>")
        .doesNotContain("9860090101191238", "2905");
    assertThat(masked("{\"pan\":\"9860090101191238\",\"code\":\"660130\"}", JSON))
        .doesNotContain("660130");
  }

  @Test
  void keycloakFormsAndCredentialsAndAnyStrayCardNumber() {
    assertThat(
            masked(
                "grant_type=client_credentials&client_id=ecom&client_secret=s3cr3t",
                "application/x-www-form-urlencoded"))
        .isEqualTo("grant_type=client_credentials&client_id=ecom&client_secret=***");
    assertThat(
            masked(
                "{\"credentials\":[{\"type\":\"password\",\"value\":\"hunter2\"}],"
                    + "\"note\":\"card 8600301234567890\"}",
                JSON))
        .doesNotContain("hunter2", "8600301234567890");
    assertThat(masked("<html>502</html>", "text/html")).isEqualTo("<16 bytes, text/html>");
  }

  @Test
  void aUriKeepsItsPathButNotItsSecrets() {
    var uri =
        URI.create(
            "http://keycloak:8080/admin/realms/ecom/users?email=aziz%40example.uz"
                + "&access_token=eyJhbGciOiJSUzI1NiJ9.x.yz12&exact=true");
    assertThat(MASKING.uri(uri))
        .isEqualTo(
            "http://keycloak:8080/admin/realms/ecom/users?email=aziz%40example.uz"
                + "&access_token=…yz12&exact=true");
    assertThat(MASKING.uri(URI.create("http://bin/internal/v1/bins/8600301234567890")))
        .isEqualTo("http://bin/internal/v1/bins/860030******7890");
  }

  @Test
  void textHidesKeyValuePairsAndStrayCardNumbers() {
    assertThat(
            MASKING.text(
                "token refresh failed: client_secret=s3cr3t pan=8600301234567890 for card"
                    + " 9860090101191238, price=$5"))
        .isEqualTo(
            "token refresh failed: client_secret=*** pan=860030******7890 for card"
                + " 986009******1238, price=$5");
    assertThat(MASKING.text(null)).isNull();
  }

  @Test
  void aServiceAddsItsOwnNamesToTheDefaults() {
    var masking =
        new Masking(
            MaskingRules.defaults().with(List.of("EMBOS_NAME"), List.of("hpan"), List.of()));
    assertThat(
            masking.body(
                "{\"EMBOS_NAME\":\"AZIZ K\",\"hpan\":\"abcdef1234\",\"password\":\"x\"}"
                    .getBytes(StandardCharsets.UTF_8),
                JSON))
        .isEqualTo("{\"EMBOS_NAME\":\"***\",\"hpan\":\"…1234\",\"password\":\"***\"}");
    assertThat(MASKING.body("{\"hpan\":\"abcdef1234\"}".getBytes(StandardCharsets.UTF_8), JSON))
        .as("the defaults alone don't know it")
        .contains("abcdef1234");
  }
}
