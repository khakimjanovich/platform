package uz.heeypay.ecom.otp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Adding the library is enough: any module can inject {@link OtpService}. */
@AutoConfiguration
@EnableConfigurationProperties(OtpProperties.class)
public class OtpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OtpService otpService(StringRedisTemplate redis, OtpProperties properties) {
    return new OtpService(redis, properties);
  }
}
