package uz.khakimjanovich.platform.otp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
@EnableConfigurationProperties(OtpProperties.class)
public class OtpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OtpService otpService(
      StringRedisTemplate redis, OtpProperties properties, ObjectProvider<MeterRegistry> meters) {
    return new OtpService(redis, properties, meters.getIfAvailable(() -> Metrics.globalRegistry));
  }
}
