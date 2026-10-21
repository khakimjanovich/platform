package uz.heeypay.ecom.otp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Adding the library to an app with Redis is enough: any module can inject {@link OtpService}. Its
 * counters go to the app's meter registry ({@code ecom.otp.issued}, {@code ecom.otp.verified},
 * {@code ecom.otp.rejected} by reason).
 */
@AutoConfiguration(afterName = OtpConstants.BOOT_DATA_REDIS)
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
