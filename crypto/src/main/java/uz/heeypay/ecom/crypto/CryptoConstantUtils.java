package uz.heeypay.ecom.crypto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CryptoConstantUtils {

  public static final String PROPERTIES_PREFIX = "ecom.crypto";
  public static final String CIPHER = "AES/GCM/NoPadding";
  public static final String KEY_ALGORITHM = "AES";
  public static final String BLIND_INDEX_ALGORITHM = "HmacSHA256";
  public static final int KEY_BYTES = 32;
  public static final int NONCE_BYTES = 12;
  public static final int TAG_BITS = 128;

  /** {@code v1:<key id>:<base64(nonce + ciphertext + tag)>}. */
  public static final String VERSION = "v1";

  public static final String SEPARATOR = ":";
  public static final String KEY_ID_PATTERN = "[A-Za-z0-9_-]{1,32}";
}
