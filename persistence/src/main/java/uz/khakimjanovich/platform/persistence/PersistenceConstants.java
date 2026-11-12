package uz.khakimjanovich.platform.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PersistenceConstants {

  public static final String PROPERTIES_PREFIX = "platform.persistence";

  public static final String HIKARI_SUFFIX = ".hikari";

  public static final String DATA_SOURCE = "DataSource";

  public static final String LIQUIBASE = "Liquibase";
  public static final String ENTITY_MANAGER_FACTORY = "EntityManagerFactory";
  public static final String TRANSACTION_MANAGER = "TransactionManager";

  static final String ENVIRONMENT_BEAN = "environment";

  public static final String UTC = "UTC";
  public static final String BATCH_SIZE = "20";

  public static final String KEEPALIVE_TOO_LONG =
      "Pool %s: keepalive-time (%d ms) must be shorter than max-lifetime (%d ms)";
}
