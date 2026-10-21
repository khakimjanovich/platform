package uz.heeypay.ecom.persistence;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
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

  /**
   * A post-processor is made before any other bean, so it can't take the {@code
   * PersistenceProperties} bean (registered above for the metadata): it binds the same prefix from
   * the environment, once.
   */
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
