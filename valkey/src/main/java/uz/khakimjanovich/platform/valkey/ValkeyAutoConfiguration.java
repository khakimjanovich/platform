package uz.khakimjanovich.platform.valkey;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Adding the library to an app with Valkey is enough: any module can inject {@link ValkeyKeys}. */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
@EnableConfigurationProperties(ValkeyProperties.class)
public class ValkeyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ValkeyKeys valkeyKeys(StringRedisTemplate valkey, ValkeyProperties properties) {
    return new ValkeyKeys(valkey, properties.service());
  }
}
