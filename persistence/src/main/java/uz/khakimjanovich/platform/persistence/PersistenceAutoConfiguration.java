package uz.khakimjanovich.platform.persistence;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@AutoConfiguration
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceAutoConfiguration {

  @Bean
  static BeanPostProcessor modulePersistenceSettings(Environment environment) {
    var properties =
        Binder.get(environment)
            .bindOrCreate(PersistenceConstants.PROPERTIES_PREFIX, PersistenceProperties.class);
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String name) {
        if (bean instanceof LocalContainerEntityManagerFactoryBean factory) {
          factory.getJpaPropertyMap().putAll(properties.jpa().properties());
        } else if (bean instanceof SpringLiquibase liquibase) {
          liquibase.setShouldRun(properties.liquibase().enabled());
        }
        return bean;
      }

      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource pool) ModulePersistence.checkKeepalive(pool);
        return bean;
      }
    };
  }
}
