package uz.heeypay.ecom.vault;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VaultConstants {

  public static final String PROPERTIES_PREFIX = "ecom.vault";
  public static final String DEFAULT_MOUNT = "secret";

  public static final String SIGNING_KEY_PATH = "merchants/%s";
  public static final String SIGNING_KEY = "signingKey";
  public static final String DEFAULT_CACHE_TTL = "30s";
  public static final String TOKEN_LOOKUP_SELF = "auth/token/lookup-self";

  /**
   * Spring Cloud Vault's auto-configuration, which makes the VaultTemplate this library reads with.
   */
  public static final String SPRING_CLOUD_VAULT =
      "org.springframework.cloud.vault.config.VaultAutoConfiguration";
}
