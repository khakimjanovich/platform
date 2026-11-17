package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.masking.Masking;
import uz.khakimjanovich.platform.masking.MaskingRules;

@ExtendWith(OutputCaptureExtension.class)
class VaultTransitTest {

  private VaultOperations vaultOperations;
  private VaultTransitOperations transitOperations;
  private VaultTransit vaultTransit;

  @BeforeEach
  void setUp() {
    vaultOperations = mock(VaultOperations.class);
    transitOperations = mock(VaultTransitOperations.class);
    when(vaultOperations.opsForTransit("transit")).thenReturn(transitOperations);
    vaultTransit = new VaultTransit(vaultOperations, "transit");
  }

  @Test
  void sanitizesPanInWarningLogWhenVaultFails(CapturedOutput output) {
    when(transitOperations.encrypt(eq("card-key"), any(Plaintext.class)))
        .thenThrow(new VaultException("Error processing card 8600301234567890 at endpoint"));

    assertThatThrownBy(() -> vaultTransit.encrypt("card-key", "ctx", "raw-secret"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE));

    assertThat(output.getOut()).contains("860030......7890").doesNotContain("8600301234567890");
  }

  @Test
  void sanitizesQualifiedContextAndSecretFieldInWarningLog(CapturedOutput output) {
    var masking = new Masking(MaskingRules.of(List.of("pan", "secret")));
    var transit = new VaultTransit(vaultOperations, "transit", masking);

    when(transitOperations.decrypt(eq("card-key"), any(Ciphertext.class)))
        .thenThrow(
            new VaultException(
                "Decrypt failed for context card.pan:8600301234567890 and field secret:supersecret"));

    assertThatThrownBy(
            () -> transit.decrypt("card-key", "card.pan:8600301234567890", "vault:v1:AAAA"))
        .isInstanceOf(GeneralException.class);

    assertThat(output.getOut())
        .contains("card.pan:860030......7890")
        .doesNotContain("8600301234567890")
        .contains("secret:supers.cret")
        .doesNotContain("supersecret");
  }

  @Test
  void sanitizesBlindIndexPanInWarningLog(CapturedOutput output) {
    when(transitOperations.getHmac(eq("index-key"), any(VaultHmacRequest.class)))
        .thenThrow(new VaultException("HMAC computation failed for pan=9860090101191238"));

    assertThatThrownBy(() -> vaultTransit.hmac("index-key", 1, "9860090101191238"))
        .isInstanceOf(GeneralException.class);

    assertThat(output.getOut()).contains("986009......1238").doesNotContain("9860090101191238");
  }

  @Test
  void refusedStatusesMapToRefusedAndOthersToUnavailable() {
    when(transitOperations.encrypt(eq("key"), any(Plaintext.class)))
        .thenThrow(
            new VaultException(
                "bad input",
                HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request", null, null, null)))
        .thenThrow(
            new VaultException(
                "not found",
                HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND, "Not Found", null, null, null)))
        .thenThrow(
            new VaultException(
                "server error",
                HttpClientErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null)))
        .thenThrow(new RestClientException("network timeout"));

    assertThatThrownBy(() -> vaultTransit.encrypt("key", "ctx", "val"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(VaultExceptionStatus.VAULT_TRANSIT_REFUSED));

    assertThatThrownBy(() -> vaultTransit.encrypt("key", "ctx", "val"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(VaultExceptionStatus.VAULT_TRANSIT_REFUSED));

    assertThatThrownBy(() -> vaultTransit.encrypt("key", "ctx", "val"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE));

    assertThatThrownBy(() -> vaultTransit.encrypt("key", "ctx", "val"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE));
  }

  @Test
  void nullParametersAreRefused() {
    assertThatThrownBy(() -> new VaultTransit(null, "transit"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new VaultTransit(vaultOperations, "transit", null))
        .isInstanceOf(NullPointerException.class);
  }
}
