package uz.khakimjanovich.platform.valkey;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

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
