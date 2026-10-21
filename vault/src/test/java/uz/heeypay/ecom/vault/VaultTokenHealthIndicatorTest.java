package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

class VaultTokenHealthIndicatorTest {

  private final VaultTemplate vault = mock(VaultTemplate.class);
  private final VaultTokenHealthIndicator indicator = new VaultTokenHealthIndicator(vault);

  @Test
  void upWhenVaultAcceptsTheToken() {
    when(vault.read("auth/token/lookup-self")).thenReturn(new VaultResponse());
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void downWhenVaultRefusesTheTokenWithoutQuotingIt() {
    when(vault.read("auth/token/lookup-self"))
        .thenThrow(new VaultException("permission denied s.secret"));
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "VaultException");
    assertThat(health.getDetails().toString()).doesNotContain("s.secret");
  }
}
