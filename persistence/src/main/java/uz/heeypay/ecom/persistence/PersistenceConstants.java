package uz.heeypay.ecom.persistence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PersistenceConstants {

  public static final String PROPERTIES_PREFIX = "ecom.persistence";
  public static final String UTC = "UTC";
  public static final String BATCH_SIZE = "20";

  public static final String KEEPALIVE_TOO_LONG =
      "Pool %s: keepalive-time (%d ms) must be shorter than max-lifetime (%d ms)";
}
