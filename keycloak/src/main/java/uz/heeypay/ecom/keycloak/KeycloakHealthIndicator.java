package uz.heeypay.ecom.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import uz.heeypay.ecom.error.GeneralException;

/**
 * UP when the app can log in to Keycloak's admin API and read from it. DOWN names only the kind of
 * failure, never a URL or the client secret.
 */
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
