package uz.khakimjanovich.platform.vault;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VaultConstants {

  public static final String PROPERTIES_PREFIX = "platform.vault";
  public static final String CLIENTS_PREFIX = "platform.vault.clients";
  public static final String CLIENTS_CACHE_TTL_MISSING =
      "platform.vault.clients.cache-ttl is required with platform.vault.clients.path";
  public static final String DEFAULT_MOUNT = "secret";

  public static final String SIGNING_KEY_PATH = "merchants/%s";
  public static final String SIGNING_KEY = "signingKey";
  public static final String DEFAULT_CACHE_TTL = "30s";
  public static final String TOKEN_LOOKUP_SELF = "auth/token/lookup-self";
  public static final String HEALTH_INDICATOR_BEAN = "vaultTokenHealthIndicator";
  public static final String HEALTH_ERROR_DETAIL = "error";
  public static final String HEALTH_EMPTY_ANSWER = "empty";

  /**
   * Spring Cloud Vault's auto-configuration, which makes the VaultTemplate this library reads with.
   */
  public static final String SPRING_CLOUD_VAULT =
      "org.springframework.cloud.vault.config.VaultAutoConfiguration";
}
