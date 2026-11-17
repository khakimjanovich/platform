package uz.khakimjanovich.platform.masking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaskingTest {

  private static final String JSON = "application/json";

  @Test
  void unconfiguredMaskingLeavesContentCompletelyUnmasked() {
    var masking = Masking.empty();
    assertThat(masking.isEnabled()).isFalse();

    var json = "{\"pan\":\"8600301234567890\",\"cvv\":\"123\",\"password\":\"s3cr3t\"}";
    assertThat(masking.body(json.getBytes(StandardCharsets.UTF_8), JSON)).isEqualTo(json);

    var text = "failed: pan=8600301234567890 cvv=123 password=s3cr3t at 12:30";
    assertThat(masking.text(text)).isEqualTo(text);

    var uri = URI.create("http://api.local/pay?pan=8600301234567890&cvv=123");
    assertThat(masking.uri(uri)).isEqualTo(uri.toString());
  }

  @Test
  void configuredFieldsMaskWithFirst6DotsLast4ForLongValues() {
    var masking = new Masking(MaskingRules.of(List.of("pan", "card_number")));
    assertThat(masking.isEnabled()).isTrue();

    var json = "{\"pan\":\"8600301234567890\",\"orderId\":9876543210123456}";
    assertThat(masking.body(json.getBytes(StandardCharsets.UTF_8), JSON))
        .isEqualTo("{\"pan\":\"860030......7890\",\"orderId\":9876543210123456}");
  }

  @Test
  void shortFieldsUnder10CharsMaskEntirelyWithDots() {
    var masking = new Masking(MaskingRules.of(List.of("cvv", "password", "pin")));

    var json = "{\"cvv\":\"123\",\"password\":\"secret\",\"pin\":\"1234\"}";
    assertThat(masking.body(json.getBytes(StandardCharsets.UTF_8), JSON))
        .isEqualTo("{\"cvv\":\"...\",\"password\":\"......\",\"pin\":\"....\"}");

    var text = "login failed: password=s3cr3t cvv=999";
    assertThat(masking.text(text)).isEqualTo("login failed: password=...... cvv=...");
  }

  @Test
  void maskingWorksAcrossXmlAndFormsAndPlainText() {
    var masking = new Masking(MaskingRules.of(List.of("pan", "client_secret", "card")));

    var xml = "<Payment><item><name>pan</name><value>9860090101191238</value></item></Payment>";
    assertThat(masking.body(xml.getBytes(StandardCharsets.UTF_8), "text/xml"))
        .contains("986009......1238");

    var form = "client_id=partner&client_secret=s3cr3t&pan=8600301234567890";
    assertThat(
            masking.body(
                form.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded"))
        .isEqualTo("client_id=partner&client_secret=......&pan=860030......7890");

    var text = "pan=8600301234567890 client_secret: topsecret paid with card 8600301234567890";
    assertThat(masking.text(text))
        .isEqualTo("pan=860030......7890 client_secret: ......... paid with card 860030......7890");

    var uri = URI.create("https://api.payment.uz/v1/checkout?pan=8600301234567890&ref=123");
    assertThat(masking.uri(uri))
        .isEqualTo("https://api.payment.uz/v1/checkout?pan=860030......7890&ref=123");
  }

  @Test
  void customMaskCharacterCanBeConfigured() {
    var masking = new Masking(MaskingRules.of("*", List.of("pan", "cvv")));

    assertThat(masking.maskValue("8600301234567890")).isEqualTo("860030******7890");
    assertThat(masking.maskValue("123")).isEqualTo("***");
  }

  @Test
  void qualifiedFieldNamesWithDotsAreMaskedWhenSuffixMatches() {
    var masking = new Masking(MaskingRules.of(List.of("pan", "cvv", "secret")));

    var text = "transit context: card.pan:8600301234567890 item.secret:supersecret order.cvv:123";
    assertThat(masking.text(text))
        .isEqualTo(
            "transit context: card.pan:860030......7890 item.secret:supers.cret order.cvv:...");
  }
}
