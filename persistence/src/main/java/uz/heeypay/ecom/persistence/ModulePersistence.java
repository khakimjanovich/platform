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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Builds one module's persistence beans. The module names them and wires them; this keeps the four
 * of them identical across modules.
 */
@UtilityClass
public class ModulePersistence {

  /** A pool that connects as the module's own user, with the module's schema as default. */
  public static HikariDataSource dataSource(String schema, ModuleDatabaseProperties db) {
    var pool = new HikariDataSource();
    pool.setPoolName(schema);
    pool.setJdbcUrl(db.getUrl());
    pool.setUsername(db.getUsername());
    pool.setPassword(db.getPassword());
    pool.setSchema(schema);
    return pool;
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
   * never changes them: Liquibase owns the schema.
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
                PhysicalNamingStrategySnakeCaseImpl.class.getName()));
    return factory;
  }

  public static JpaTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }
}
