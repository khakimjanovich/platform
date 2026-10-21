package uz.heeypay.ecom.crypto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CryptoConstants {

  public static final String PROPERTIES_PREFIX = "ecom.crypto";
  public static final String CIPHER = "AES/GCM/NoPadding";
  public static final String KEY_ALGORITHM = "AES";
  public static final String BLIND_INDEX_ALGORITHM = "HmacSHA256";
  public static final int KEY_BYTES = 32;
  public static final int NONCE_BYTES = 12;
  public static final int TAG_BITS = 128;

  /** {@code v1:<key id>:<base64(nonce + ciphertext + tag)>}, bound to its key id. */
  public static final String VERSION = "v1";

  /** {@code v2:<key id>:<base64(nonce + ciphertext + tag)>}, bound to its key id and context. */
  public static final String VERSION_2 = "v2";

  /** Between the key id and the context in a v2 value's associated data. */
  public static final byte CONTEXT_SEPARATOR = 0x00;

  public static final String SEPARATOR = ":";
  public static final String KEY_ID_PATTERN = "[A-Za-z0-9_-]{1,32}";
}
