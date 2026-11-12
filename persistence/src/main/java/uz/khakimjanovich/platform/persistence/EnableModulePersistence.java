package uz.khakimjanovich.platform.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * A module's pool, migrations, entity manager and transactions, named {@code <schema>DataSource},
 * {@code <schema>Liquibase}, {@code <schema>EntityManagerFactory} and {@code
 * <schema>TransactionManager}. The pool binds {@code <prefix>} and {@code <prefix>.hikari.*}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ModulePersistenceRegistrar.class)
public @interface EnableModulePersistence {

  String schema();

  /** e.g. {@code app.db.card} */
  String prefix();

  String changelog();

  /** Any entity of the module; its package is scanned. */
  Class<?> entities();
}
