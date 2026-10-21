package uz.heeypay.ecom.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Every module's pool is checked once its settings are bound. */
@AutoConfiguration
public class PersistenceAutoConfiguration {

  @Bean
  static BeanPostProcessor hikariSettingsCheck() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource pool) ModulePersistence.checkKeepalive(pool);
        return bean;
      }
    };
  }
}
