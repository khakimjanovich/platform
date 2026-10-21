package uz.heeypay.ecom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
