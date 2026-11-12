package uz.khakimjanovich.platform.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import uz.khakimjanovich.platform.error.GeneralException;

@RequiredArgsConstructor
class KeycloakHealthIndicator implements HealthIndicator {

  private final KeycloakAdmin admin;

  @Override
  public Health health() {
    try {
      admin.ping();
      return Health.up().build();
    } catch (GeneralException e) {
      return Health.down()
          .withDetail(KeycloakConstants.HEALTH_ERROR_DETAIL, e.getStatus().getType())
          .build();
    }
  }
}
