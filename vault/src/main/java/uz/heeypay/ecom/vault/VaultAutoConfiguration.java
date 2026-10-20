package uz.heeypay.ecom.vault;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/** Setting {@code ecom.vault.url} gives the app {@link VaultSecrets} and {@link SigningKeys}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = VaultConstants.PROPERTIES_PREFIX, name = "url")
@EnableConfigurationProperties(VaultProperties.class)
public class VaultAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  VaultSecrets vaultSecrets(
      VaultProperties properties, ObjectProvider<RestClient.Builder> builders) {
    return new VaultSecrets(
        builders
            .getIfAvailable(RestClient::builder)
            .baseUrl(properties.url())
            .defaultHeader(VaultConstants.TOKEN_HEADER, properties.token())
            .build(),
        properties.mount());
  }

  @Bean
  @ConditionalOnMissingBean
  SigningKeys signingKeys(VaultSecrets vaultSecrets) {
    return new SigningKeys(vaultSecrets);
  }
}
