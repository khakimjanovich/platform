package uz.heeypay.ecom.vault;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestClientException;

/**
 * UP when Vault accepts the app's token (a token lookup of itself, allowed by Vault's default
 * policy). Spring Cloud Vault's own indicator, {@code vault}, only checks that Vault is up and
 * unsealed, which an expired or revoked token doesn't change. DOWN names the kind of failure, never
 * a URL or the token.
 */
@RequiredArgsConstructor
class VaultTokenHealthIndicator implements HealthIndicator {

  private final VaultTemplate vault;

  @Override
  public Health health() {
    try {
      var self = vault.read(VaultConstants.TOKEN_LOOKUP_SELF);
      return self == null
          ? Health.down()
              .withDetail(VaultConstants.HEALTH_ERROR_DETAIL, VaultConstants.HEALTH_EMPTY_ANSWER)
              .build()
          : Health.up().build();
    } catch (VaultException | RestClientException e) {
      return Health.down()
          .withDetail(VaultConstants.HEALTH_ERROR_DETAIL, e.getClass().getSimpleName())
          .build();
    }
  }
}
