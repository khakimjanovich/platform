package uz.khakimjanovich.platform.persistence;

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

@UtilityClass
public class ModulePersistence {

  public static HikariDataSource dataSource(String schema, DataSourceProperties db) {
    var pool = db.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    pool.setPoolName(schema);
    pool.setSchema(schema);
    return pool;
  }

  public static void checkKeepalive(HikariDataSource pool) {
    var keepalive = pool.getKeepaliveTime();
    var lifetime = pool.getMaxLifetime();
    if (keepalive > 0 && lifetime > 0 && keepalive >= lifetime) {
      throw new IllegalStateException(
          PersistenceConstants.KEEPALIVE_TOO_LONG.formatted(
              pool.getPoolName(), keepalive, lifetime));
    }
  }

  public static SpringLiquibase liquibase(String schema, String changelog, DataSource dataSource) {
    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changelog);
    liquibase.setDefaultSchema(schema);
    liquibase.setLiquibaseSchema(schema);
    return liquibase;
  }

  public static LocalContainerEntityManagerFactoryBean entityManagerFactory(
      String schema, Class<?> anyEntity, DataSource dataSource) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setPersistenceUnitName(schema);
    factory.setDataSource(dataSource);
    factory.setPackagesToScan(anyEntity.getPackageName());
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            AvailableSettings.HBM2DDL_AUTO,
            Action.VALIDATE.getExternalHbm2ddlName(),
            AvailableSettings.DEFAULT_SCHEMA,
            schema,
            AvailableSettings.PHYSICAL_NAMING_STRATEGY,
            PhysicalNamingStrategySnakeCaseImpl.class.getName(),
            AvailableSettings.JDBC_TIME_ZONE,
            PersistenceConstants.UTC,
            AvailableSettings.STATEMENT_BATCH_SIZE,
            PersistenceConstants.BATCH_SIZE,
            AvailableSettings.ORDER_INSERTS,
            "true",
            AvailableSettings.ORDER_UPDATES,
            "true"));
    return factory;
  }

  public static JpaTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }
}
