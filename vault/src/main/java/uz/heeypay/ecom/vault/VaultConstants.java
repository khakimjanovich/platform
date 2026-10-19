package uz.heeypay.ecom.vault;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VaultConstants {

  public static final String PROPERTIES_PREFIX = "ecom.vault";
  public static final String TOKEN_HEADER = "X-Vault-Token";

  /** KV version 2 keeps values under {@code <mount>/data/<path>}, wrapped in {@code data}. */
  public static final String DATA_PATH = "/v1/{mount}/data/{path}";

  public static final String DATA = "data";
}
