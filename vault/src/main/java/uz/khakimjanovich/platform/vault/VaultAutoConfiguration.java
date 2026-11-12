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
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;

/**
 * Where Spring Cloud Vault gives the app a {@link VaultTemplate} ({@code spring.cloud.vault.*}),
 * this library gives it {@link VaultSecrets}; with {@code platform.vault.signing-keys.path}, {@link
 * SigningKeys}; with {@code platform.vault.clients.path}, a {@link ClientRegistry}; and with {@code
 * platform.vault.transit.mount}, {@link VaultTransit}.
 */
@AutoConfiguration(afterName = VaultConstants.SPRING_CLOUD_VAULT)
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties({
  VaultProperties.class,
  VaultSigningKeysProperties.class,
  VaultClientsProperties.class
})
public class VaultAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  VaultSecrets vaultSecrets(VaultTemplate vaultTemplate, VaultProperties properties) {
    return new VaultSecrets(vaultTemplate, properties.mount());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = VaultConstants.SIGNING_KEYS_PREFIX, name = "path")
  SigningKeys signingKeys(VaultSecrets vaultSecrets, VaultSigningKeysProperties properties) {
    var field =
        Objects.requireNonNull(properties.field(), VaultConstants.SIGNING_KEYS_FIELD_MISSING);
    var ttl =
        Objects.requireNonNull(
            properties.cacheTtl(), VaultConstants.SIGNING_KEYS_CACHE_TTL_MISSING);
    return new SigningKeys(
        vaultSecrets, Objects.requireNonNull(properties.path()), field, ttl, Clock.systemUTC());
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

  /**
   * Transit only for an app that names its mount: its properties are bound only then, so an app
   * without Transit needs no setting, and a blank mount stops startup.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = VaultConstants.TRANSIT_PREFIX, name = "mount")
  @EnableConfigurationProperties(VaultTransitProperties.class)
  static class Transit {

    @Bean
    @ConditionalOnMissingBean
    VaultTransit vaultTransit(VaultTemplate vaultTemplate, VaultTransitProperties properties) {
      return new VaultTransit(vaultTemplate, properties.mount());
    }
  }

  /** With the actuator: a token Vault refuses turns the app's health DOWN. */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = VaultConstants.HEALTH_INDICATOR_BEAN)
  HealthIndicator vaultTokenHealthIndicator(VaultTemplate vaultTemplate) {
    return new VaultTokenHealthIndicator(vaultTemplate);
  }
}
