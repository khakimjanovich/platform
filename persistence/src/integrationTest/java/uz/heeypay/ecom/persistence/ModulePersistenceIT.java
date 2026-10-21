package uz.heeypay.ecom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.ResourceBundle;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Two modules on one real Postgres, wired as every module is: each migrates its own schema as its
 * own user, can't read the other's, and its pool takes its own settings; an entity ahead of its
 * migrations stops startup.
 */
@SpringBootTest(
    classes = {ModulePersistenceIT.ModuleA.class, ModulePersistenceIT.ModuleB.class},
    properties = "ecom.db.a.hikari.maximum-pool-size=3")
@Testcontainers
class ModulePersistenceIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(ResourceBundle.getBundle("images").getString("postgres"))
          .withInitScript("persistence-db.sql");

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    for (var module : new String[] {"a", "b", "c"}) {
      registry.add("ecom.db." + module + ".url", POSTGRES::getJdbcUrl);
      registry.add("ecom.db." + module + ".username", () -> module + "_user");
      registry.add("ecom.db." + module + ".password", () -> "test");
    }
  }

  /** What each module's *PersistenceConfig declares, for module "a". */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties
  @ImportAutoConfiguration(PersistenceAutoConfiguration.class)
  static class ModuleA {
    @Bean
    @ConfigurationProperties("ecom.db.a")
    DataSourceProperties aDataSourceProperties() {
      return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("ecom.db.a.hikari")
    HikariDataSource aDataSource(DataSourceProperties aDataSourceProperties) {
      return ModulePersistence.dataSource("a", aDataSourceProperties);
    }

    @Bean
    SpringLiquibase aLiquibase(@Qualifier("aDataSource") DataSource dataSource) {
      return ModulePersistence.liquibase("a", "db/a/changelog.yaml", dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean aEntityManagerFactory(
        @Qualifier("aDataSource") DataSource dataSource,
        @Qualifier("aLiquibase") SpringLiquibase migrationsFirst) {
      return ModulePersistence.entityManagerFactory(
          "a", uz.heeypay.ecom.persistence.a.Note.class, dataSource);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ModuleB {
    @Bean
    @ConfigurationProperties("ecom.db.b")
    DataSourceProperties bDataSourceProperties() {
      return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("ecom.db.b.hikari")
    HikariDataSource bDataSource(DataSourceProperties bDataSourceProperties) {
      return ModulePersistence.dataSource("b", bDataSourceProperties);
    }

    @Bean
    SpringLiquibase bLiquibase(@Qualifier("bDataSource") DataSource dataSource) {
      return ModulePersistence.liquibase("b", "db/b/changelog.yaml", dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean bEntityManagerFactory(
        @Qualifier("bDataSource") DataSource dataSource,
        @Qualifier("bLiquibase") SpringLiquibase migrationsFirst) {
      return ModulePersistence.entityManagerFactory(
          "b", uz.heeypay.ecom.persistence.b.Note.class, dataSource);
    }
  }

  /** Module "c": its entity has a column its migrations never created. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties
  @ImportAutoConfiguration(PersistenceAutoConfiguration.class)
  static class ModuleC {
    @Bean
    @ConfigurationProperties("ecom.db.c")
    DataSourceProperties cDataSourceProperties() {
      return new DataSourceProperties();
    }

    @Bean
    HikariDataSource cDataSource(DataSourceProperties cDataSourceProperties) {
      return ModulePersistence.dataSource("c", cDataSourceProperties);
    }

    @Bean
    SpringLiquibase cLiquibase(@Qualifier("cDataSource") DataSource dataSource) {
      return ModulePersistence.liquibase("c", "db/c/changelog.yaml", dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean cEntityManagerFactory(
        @Qualifier("cDataSource") DataSource dataSource,
        @Qualifier("cLiquibase") SpringLiquibase migrationsFirst) {
      return ModulePersistence.entityManagerFactory(
          "c", uz.heeypay.ecom.persistence.c.Note.class, dataSource);
    }
  }

  @Autowired
  @Qualifier("aDataSource")
  HikariDataSource a;

  @Autowired
  @Qualifier("bDataSource")
  HikariDataSource b;

  @Test
  void eachModuleMigratesItsOwnSchemaAndKeepsItsBookkeepingThere() {
    var jdbc = new JdbcTemplate(a);
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'a'",
                String.class))
        .contains("note", "databasechangelog", "databasechangeloglock");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'",
                Integer.class))
        .isZero();
  }

  @Test
  void aModulesUserCantReadAnotherModulesSchema() {
    assertThat(new JdbcTemplate(b).queryForObject("select count(*) from note", Integer.class))
        .isZero();
    assertThatThrownBy(
            () -> new JdbcTemplate(a).queryForObject("select count(*) from b.note", Integer.class))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("permission denied for schema b");
  }

  @Test
  void eachPoolTakesItsOwnSettings() {
    assertThat(a.getMaximumPoolSize()).isEqualTo(3);
    assertThat(b.getMaximumPoolSize()).as("Hikari's own default").isEqualTo(10);
    assertThat(a.getPoolName()).isEqualTo("a");
  }

  @Test
  void anEntityAheadOfItsMigrationsStopsStartup() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(ModuleC.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                        "ecom.db.c.url=" + POSTGRES.getJdbcUrl(),
                        "ecom.db.c.username=c_user",
                        "ecom.db.c.password=test")
                    .run()
                    .close())
        .hasStackTraceContaining("author");
  }
}
