package uz.heeypay.ecom.persistence;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What every module's entity manager and migrations get, app-wide. Spring Boot's own {@code
 * spring.jpa.*} and {@code spring.liquibase.*} don't reach a module's own entity manager factory
 * and Liquibase, so these do.
 *
 * @param jpa JPA settings for every module's entity manager
 * @param liquibase every module's migrations
 */
@ConfigurationProperties(PersistenceConstants.PROPERTIES_PREFIX)
public record PersistenceProperties(@DefaultValue Jpa jpa, @DefaultValue Liquibase liquibase) {

  /**
   * Hibernate settings on top of the library's defaults (UTC, batches of 20, ordered inserts and
   * updates); a value here wins.
   *
   * @param properties Hibernate properties by name, e.g. {@code hibernate.jdbc.batch_size}
   */
  public record Jpa(@DefaultValue Map<String, String> properties) {}

  /**
   * Whether the modules migrate their schemas at startup.
   *
   * @param enabled false where migrations run apart from the app, e.g. as a deploy step
   */
  public record Liquibase(@DefaultValue("true") boolean enabled) {}
}
