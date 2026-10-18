package uz.heeypay.ecom.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Setting {@code ecom.crypto.active-key-id} gives the app a {@link Crypto}; bad keys stop startup.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = CryptoConstantUtils.PROPERTIES_PREFIX, name = "active-key-id")
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Crypto crypto(CryptoProperties properties) {
    return new Crypto(properties);
  }
}
