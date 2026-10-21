package uz.heeypay.ecom.persistence;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * What every module's persistence beans get without a change to their configuration: each pool is
 * checked once its settings are bound, each entity manager factory gets {@code
 * ecom.persistence.jpa.properties} on top of the library's defaults before it starts, and each
 * module's Liquibase follows {@code ecom.persistence.liquibase.enabled}.
 */
@AutoConfiguration
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceAutoConfiguration {

  @Bean
  static BeanPostProcessor modulePersistenceSettings(Environment environment) {
    return new BeanPostProcessor() {
      private @Nullable PersistenceProperties properties;

      @Override
      public Object postProcessBeforeInitialization(Object bean, String name) {
        if (bean instanceof LocalContainerEntityManagerFactoryBean factory) {
          factory.getJpaPropertyMap().putAll(properties().jpa().properties());
        } else if (bean instanceof SpringLiquibase liquibase) {
          liquibase.setShouldRun(properties().liquibase().enabled());
        }
        return bean;
      }

      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource pool) ModulePersistence.checkKeepalive(pool);
        return bean;
      }

      /** Bound once, from the environment: a post-processor comes before other beans. */
      private PersistenceProperties properties() {
        if (properties == null) {
          properties =
              Binder.get(environment)
                  .bindOrCreate(
                      PersistenceConstants.PROPERTIES_PREFIX, PersistenceProperties.class);
        }
        return properties;
      }
    };
  }
}
