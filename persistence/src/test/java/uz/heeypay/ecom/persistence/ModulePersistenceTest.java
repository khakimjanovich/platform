package uz.heeypay.ecom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/** One module's beans, built the same way for every module. */
class ModulePersistenceTest {

  private static DataSourceProperties database() {
    var database = new DataSourceProperties();
    database.setUrl("jdbc:postgresql://localhost:5442/acquiring");
    database.setUsername("card_user");
    database.setPassword("local-only");
    return database;
  }

  @Test
  void thePoolIsNamedAfterItsSchemaAndConnectsToIt() {
    try (var pool = ModulePersistence.dataSource("card", database())) {
      assertThat(pool.getPoolName()).isEqualTo("card");
      assertThat(pool.getSchema()).isEqualTo("card");
      assertThat(pool.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5442/acquiring");
      assertThat(pool.getUsername()).isEqualTo("card_user");
    }
  }

  @Test
  void aKeepaliveNoShorterThanTheLifetimeStopsStartup() {
    var pool = ModulePersistence.dataSource("card", database());
    pool.setKeepaliveTime(300_000);
    pool.setMaxLifetime(180_000);
    assertThatThrownBy(() -> ModulePersistence.checkKeepalive(pool))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("card")
        .hasMessageContaining("keepalive-time");
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PersistenceAutoConfiguration.class))
        .withBean("cardDataSource", HikariDataSource.class, () -> pool)
        .run(context -> assertThat(context).hasFailed());
  }

  /** A module's pool settings as ecom-infra passes them: an environment variable per setting. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties
  static class CardPool {
    @Bean
    @ConfigurationProperties("ecom.db.card")
    DataSourceProperties cardDataSourceProperties() {
      return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("ecom.db.card" + PersistenceConstants.HIKARI_SUFFIX)
    HikariDataSource cardDataSource(DataSourceProperties cardDataSourceProperties) {
      return ModulePersistence.dataSource("card", cardDataSourceProperties);
    }
  }

  @Test
  void aPoolSettingComesFromAnEnvironmentVariable() {
    new ApplicationContextRunner()
        .withUserConfiguration(CardPool.class)
        .withPropertyValues(
            "ecom.db.card.url=jdbc:postgresql://localhost:5442/acquiring",
            "ecom.db.card.username=card_user")
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            Map.of("ECOM_DB_CARD_HIKARI_MAXIMUM_POOL_SIZE", "3"))))
        .run(
            context ->
                assertThat(context.getBean(HikariDataSource.class).getMaximumPoolSize())
                    .isEqualTo(3));
  }

  @Test
  void aShorterKeepaliveOrNoneIsFine() {
    try (var pool = ModulePersistence.dataSource("card", database())) {
      ModulePersistence.checkKeepalive(pool);
      pool.setKeepaliveTime(120_000);
      pool.setMaxLifetime(1_800_000);
      ModulePersistence.checkKeepalive(pool);
    }
  }

  @Test
  void hibernateValidatesTheTablesAndNeverChangesThem() {
    try (var pool = ModulePersistence.dataSource("card", database())) {
      var factory =
          ModulePersistence.entityManagerFactory("card", ModulePersistenceTest.class, pool);
      assertThat(factory.getJpaPropertyMap())
          .containsEntry("hibernate.hbm2ddl.auto", "validate")
          .containsEntry("hibernate.default_schema", "card");
    }
  }

  @Test
  void everyEntityManagerGetsUtcAndOrderedBatches() {
    try (var pool = ModulePersistence.dataSource("card", database())) {
      var factory =
          ModulePersistence.entityManagerFactory("card", ModulePersistenceTest.class, pool);
      assertThat(factory.getJpaPropertyMap())
          .containsEntry("hibernate.jdbc.time_zone", "UTC")
          .containsEntry("hibernate.jdbc.batch_size", "20")
          .containsEntry("hibernate.order_inserts", "true")
          .containsEntry("hibernate.order_updates", "true");
    }
  }

  @Test
  void theAppsJpaPropertiesWinAndMigrationsCanBeSwitchedOff() {
    var pool = ModulePersistence.dataSource("card", database());
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PersistenceAutoConfiguration.class))
        .withPropertyValues(
            "ecom.persistence.jpa.properties[hibernate.jdbc.batch_size]=50",
            "ecom.persistence.jpa.properties[hibernate.generate_statistics]=true",
            "ecom.persistence.liquibase.enabled=false")
        .run(
            context -> {
              var postProcessor =
                  context.getBean("modulePersistenceSettings", BeanPostProcessor.class);
              var factory =
                  ModulePersistence.entityManagerFactory("card", ModulePersistenceTest.class, pool);
              postProcessor.postProcessBeforeInitialization(factory, "cardEntityManagerFactory");
              assertThat(factory.getJpaPropertyMap())
                  .containsEntry("hibernate.jdbc.batch_size", "50")
                  .containsEntry("hibernate.generate_statistics", "true")
                  .containsEntry("hibernate.jdbc.time_zone", "UTC");
              var liquibase = ModulePersistence.liquibase("card", "db/card.yaml", pool);
              postProcessor.postProcessBeforeInitialization(liquibase, "cardLiquibase");
              assertThat(ReflectionTestUtils.getField(liquibase, "shouldRun")).isEqualTo(false);
            });
    pool.close();
  }
}
