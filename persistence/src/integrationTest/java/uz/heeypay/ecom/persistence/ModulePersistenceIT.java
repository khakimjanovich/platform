package uz.heeypay.ecom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.core.env.Environment;
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
    for (var module : new String[] {"a", "b", "c", "d"}) {
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

    /** The pool as module a's entity manager sees it, counting the batches it sends. */
    @Bean
    CountingDataSource aCounting(@Qualifier("aDataSource") DataSource dataSource) {
      return new CountingDataSource(dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean aEntityManagerFactory(
        CountingDataSource aCounting, @Qualifier("aLiquibase") SpringLiquibase migrationsFirst) {
      return ModulePersistence.entityManagerFactory(
          "a", uz.heeypay.ecom.persistence.a.Note.class, aCounting.dataSource);
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

  @Autowired CountingDataSource aCounting;

  @Autowired
  @Qualifier("aEntityManagerFactory")
  EntityManagerFactory aEntities;

  @Autowired Environment environment;

  /** Only migrations, for module "d", to see them switched off. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties
  @ImportAutoConfiguration(PersistenceAutoConfiguration.class)
  static class ModuleD {
    @Bean
    @ConfigurationProperties("ecom.db.d")
    DataSourceProperties dDataSourceProperties() {
      return new DataSourceProperties();
    }

    @Bean
    HikariDataSource dDataSource(DataSourceProperties dDataSourceProperties) {
      return ModulePersistence.dataSource("d", dDataSourceProperties);
    }

    @Bean
    SpringLiquibase dLiquibase(@Qualifier("dDataSource") DataSource dataSource) {
      return ModulePersistence.liquibase("d", "db/d/changelog.yaml", dataSource);
    }
  }

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
  void theLibraryDefaultsLoadWithoutAnImport() {
    assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
  }

  @Test
  void eachPoolTakesItsOwnSettings() {
    assertThat(a.getMaximumPoolSize()).isEqualTo(3);
    assertThat(b.getMaximumPoolSize()).as("Hikari's own default").isEqualTo(10);
    assertThat(a.getPoolName()).isEqualTo("a");
  }

  @Test
  void fiftyInsertsGoInThreeBatches() {
    var entities = aEntities.createEntityManager();
    entities.getTransaction().begin();
    for (long i = 1; i <= 50; i++) {
      var note = new uz.heeypay.ecom.persistence.a.Note();
      note.id = 1000 + i;
      note.text = "note " + i;
      entities.persist(note);
    }
    var before = aCounting.batches.get();
    entities.getTransaction().commit();
    entities.close();
    assertThat(aCounting.batches.get() - before).as("20 + 20 + 10").isEqualTo(3);
  }

  @Test
  void aTimeIsStoredAndReadAsUtc() {
    var written = OffsetDateTime.of(2026, 10, 21, 15, 30, 0, 0, ZoneOffset.ofHours(5));
    var entities = aEntities.createEntityManager();
    entities.getTransaction().begin();
    var note = new uz.heeypay.ecom.persistence.a.Note();
    note.id = 1L;
    note.writtenAt = written;
    entities.persist(note);
    entities.getTransaction().commit();
    entities.clear();
    var read = entities.find(uz.heeypay.ecom.persistence.a.Note.class, 1L).writtenAt;
    entities.close();
    assertThat(read.getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(read.toInstant()).isEqualTo(written.toInstant());
  }

  @Test
  void migrationsCanBeSwitchedOff() {
    try (var context =
        new SpringApplicationBuilder(ModuleD.class)
            .web(WebApplicationType.NONE)
            .properties(
                "ecom.db.d.url=" + POSTGRES.getJdbcUrl(),
                "ecom.db.d.username=d_user",
                "ecom.db.d.password=test",
                "ecom.persistence.liquibase.enabled=false")
            .run()) {
      var tables =
          new JdbcTemplate(context.getBean("dDataSource", DataSource.class))
              .queryForObject(
                  "select count(*) from information_schema.tables where table_schema = 'd'",
                  Integer.class);
      assertThat(tables).isZero();
    }
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
