package uz.heeypay.ecom.vault;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/** Setting {@code ecom.vault.url} gives the app {@link VaultSecrets}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = VaultConstants.PROPERTIES_PREFIX, name = "url")
@EnableConfigurationProperties(VaultProperties.class)
public class VaultAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  VaultSecrets vaultSecrets(VaultProperties properties) {
    return new VaultSecrets(
        RestClient.builder()
            .baseUrl(properties.url())
            .defaultHeader(VaultConstants.TOKEN_HEADER, properties.token())
            .build(),
        properties.mount());
  }
}
