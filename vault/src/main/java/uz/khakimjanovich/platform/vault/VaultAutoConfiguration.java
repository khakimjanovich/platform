package uz.khakimjanovich.platform.vault;

import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

/**
 * Where Spring Cloud Vault gives the app a {@link VaultTemplate} ({@code spring.cloud.vault.*}),
 * this library gives it {@link VaultSecrets} and {@link SigningKeys}, and, with {@code
 * platform.vault.clients.path}, a {@link ClientRegistry}.
 */
@AutoConfiguration(afterName = VaultConstants.SPRING_CLOUD_VAULT)
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties({VaultProperties.class, VaultClientsProperties.class})
public class VaultAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  VaultSecrets vaultSecrets(VaultTemplate vaultTemplate, VaultProperties properties) {
    return new VaultSecrets(vaultTemplate, properties.mount());
  }

  @Bean
  @ConditionalOnMissingBean
  SigningKeys signingKeys(VaultSecrets vaultSecrets, VaultProperties properties) {
    var keys = properties.signingKeys();
    return new SigningKeys(vaultSecrets, keys.path(), keys.cacheTtl(), Clock.systemUTC());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = VaultConstants.CLIENTS_PREFIX, name = "path")
  ClientRegistry clientRegistry(VaultSecrets vaultSecrets, VaultClientsProperties properties) {
    var ttl =
        Objects.requireNonNull(properties.cacheTtl(), VaultConstants.CLIENTS_CACHE_TTL_MISSING);
    return new ClientRegistry(
        vaultSecrets, Objects.requireNonNull(properties.path()), ttl, Clock.systemUTC());
  }

  /** With the actuator: a token Vault refuses turns the app's health DOWN. */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = VaultConstants.HEALTH_INDICATOR_BEAN)
  HealthIndicator vaultTokenHealthIndicator(VaultTemplate vaultTemplate) {
    return new VaultTokenHealthIndicator(vaultTemplate);
  }
}
