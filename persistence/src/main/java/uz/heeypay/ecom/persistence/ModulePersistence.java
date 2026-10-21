package uz.heeypay.ecom.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.experimental.UtilityClass;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.Action;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Builds one module's persistence beans. The module names them and wires them; this keeps the four
 * of them identical across modules.
 */
@UtilityClass
public class ModulePersistence {

  /**
   * A pool that connects as the module's own user (Boot's {@link DataSourceProperties}, bound to
   * the module's prefix, e.g. {@code ecom.db.card}), with the module's schema as default. The
   * module's bean method binds {@code <prefix>.hikari.*} onto it, so every Hikari setting (pool
   * size, timeouts, keepalive) is set per environment; unset ones are Hikari's own defaults.
   */
  public static HikariDataSource dataSource(String schema, DataSourceProperties db) {
    var pool = db.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    pool.setPoolName(schema);
    pool.setSchema(schema);
    return pool;
  }

  /**
   * Hikari quietly drops a keepalive that isn't shorter than the connections' lifetime; a pool set
   * up that way is a mistake, so it stops startup instead.
   */
  public static void checkKeepalive(HikariDataSource pool) {
    var keepalive = pool.getKeepaliveTime();
    var lifetime = pool.getMaxLifetime();
    if (keepalive > 0 && lifetime > 0 && keepalive >= lifetime) {
      throw new IllegalStateException(
          PersistenceConstants.KEEPALIVE_TOO_LONG.formatted(
              pool.getPoolName(), keepalive, lifetime));
    }
  }

  /** The module's migrations; Liquibase keeps its own bookkeeping tables inside the schema too. */
  public static SpringLiquibase liquibase(String schema, String changelog, DataSource dataSource) {
    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changelog);
    liquibase.setDefaultSchema(schema);
    liquibase.setLiquibaseSchema(schema);
    return liquibase;
  }

  /**
   * Hibernate for the module's entities only. It validates the tables against the entities and
   * never changes them: Liquibase owns the schema. Times are written as UTC, and inserts and
   * updates go in ordered batches; {@code ecom.persistence.jpa.properties} adds to or overrides
   * these for every module.
   */
  public static LocalContainerEntityManagerFactoryBean entityManagerFactory(
      String schema, Class<?> anyEntity, DataSource dataSource) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setPersistenceUnitName(schema);
    factory.setDataSource(dataSource);
    factory.setPackagesToScan(anyEntity.getPackageName());
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            AvailableSettings.HBM2DDL_AUTO, Action.VALIDATE.getExternalHbm2ddlName(),
            AvailableSettings.DEFAULT_SCHEMA, schema,
            AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                PhysicalNamingStrategySnakeCaseImpl.class.getName(),
            AvailableSettings.JDBC_TIME_ZONE, PersistenceConstants.UTC,
            AvailableSettings.STATEMENT_BATCH_SIZE, PersistenceConstants.BATCH_SIZE,
            AvailableSettings.ORDER_INSERTS, Boolean.TRUE.toString(),
            AvailableSettings.ORDER_UPDATES, Boolean.TRUE.toString()));
    return factory;
  }

  public static JpaTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }
}
