package uz.heeypay.ecom.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import uz.heeypay.ecom.error.GeneralException;

class KeycloakHealthIndicatorTest {

  private final KeycloakAdmin admin = mock(KeycloakAdmin.class);
  private final KeycloakHealthIndicator indicator = new KeycloakHealthIndicator(admin);

  @Test
  void upWhenTheAdminApiAnswers() {
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void downWithTheErrorCodeWhenNoTokenCanBeObtained() {
    doThrow(new GeneralException(KeycloakExceptionStatus.KEYCLOAK_UNAVAILABLE)).when(admin).ping();
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "KEYCLOAK_UNAVAILABLE");
  }
}
