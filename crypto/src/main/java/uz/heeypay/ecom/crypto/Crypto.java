package uz.heeypay.ecom.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * AES-256-GCM: every value gets a fresh 12-byte nonce, and the key id is authenticated with it, so
 * a ciphertext can't be moved to another key unnoticed. A {@code v2} value also authenticates its
 * context (which row and field it belongs to), so it can't be moved to another card or column
 * either. A tampered value fails to decrypt.
 */
public class Crypto {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final Map<String, SecretKey> keys;
  private final String activeKeyId;
  private final SecretKey blindIndexKey;

  public Crypto(CryptoProperties properties) {
    if (properties.keys() == null || !properties.keys().containsKey(properties.activeKeyId())) {
      throw new IllegalStateException(
          "ecom.crypto.active-key-id must name one of ecom.crypto.keys");
    }
    this.keys =
        properties.keys().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, e -> key(e.getKey(), e.getValue())));
    this.activeKeyId = properties.activeKeyId();
    this.blindIndexKey =
        new SecretKeySpec(
            decodeKey("blind-index-key", properties.blindIndexKey()),
            CryptoConstants.BLIND_INDEX_ALGORITHM);
  }

  /**
   * Seals a value bound only to its key ({@code v1}). Prefer {@link #encrypt(String, String)}: a
   * {@code v1} value can be moved to another row or field unnoticed.
   */
  public String encrypt(String plain) {
    return seal(plain, CryptoConstants.VERSION, null);
  }

  /**
   * Seals a value bound to where it lives ({@code v2}), e.g. {@code card.pan:<card id>}: it
   * decrypts only with the same context, so a ciphertext copied to another row or field fails.
   */
  public String encrypt(String plain, String context) {
    return seal(plain, CryptoConstants.VERSION_2, context);
  }

  /** Opens a {@code v1} value. A {@code v2} value needs its context. */
  public String decrypt(String envelope) {
    return open(envelope, null);
  }

  /**
   * Opens a {@code v2} value sealed with this context, or a {@code v1} value (which has none), so
   * data reads the same before and after its migration.
   */
  public String decrypt(String envelope, String context) {
    return open(envelope, context);
  }

  /** Every key id this instance can read, for a rotation job that checks what is still in use. */
  public Set<String> keyIds() {
    return keys.keySet();
  }

  /** The key new values are sealed with. */
  public String activeKeyId() {
    return activeKeyId;
  }

  /** Whether the value was sealed with a key other than the active one, and should be rewrapped. */
  public boolean isStale(String envelope) {
    var parts = envelope.split(CryptoConstants.SEPARATOR, 3);
    return parts.length != 3 || !activeKeyId.equals(parts[1]);
  }

  /** Re-encrypts a {@code v1} value under the active key, for key rotation. */
  public String rewrap(String envelope) {
    return isStale(envelope) ? encrypt(decrypt(envelope)) : envelope;
  }

  /**
   * Re-encrypts under the active key and binds the value to its context: a {@code v1} value becomes
   * {@code v2}, a {@code v2} value under an old key moves to the active one.
   */
  public String rewrap(String envelope, String context) {
    var current = envelope.startsWith(CryptoConstants.VERSION_2 + CryptoConstants.SEPARATOR);
    return current && !isStale(envelope) ? envelope : encrypt(decrypt(envelope, context), context);
  }

  /**
   * The same input always gives the same index, so equal values can be found without decrypting.
   */
  public String blindIndex(String plain) {
    try {
      var mac = Mac.getInstance(CryptoConstants.BLIND_INDEX_ALGORITHM);
      mac.init(blindIndexKey);
      return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("blind index failed", e);
    }
  }

  private String seal(String plain, String version, @Nullable String context) {
    try {
      var nonce = new byte[CryptoConstants.NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      var cipher = cipher(Cipher.ENCRYPT_MODE, activeKeyId, nonce, context);
      var sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      var out = new byte[nonce.length + sealed.length];
      System.arraycopy(nonce, 0, out, 0, nonce.length);
      System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
      return String.join(
          CryptoConstants.SEPARATOR, version, activeKeyId, Base64.getEncoder().encodeToString(out));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("encryption failed", e);
    }
  }

  private String open(String envelope, @Nullable String context) {
    var parts = envelope.split(CryptoConstants.SEPARATOR, 3);
    if (parts.length != 3) throw new IllegalArgumentException("not an encrypted value");
    @Nullable String bound =
        switch (parts[0]) {
          case CryptoConstants.VERSION -> null;
          case CryptoConstants.VERSION_2 -> {
            if (context == null) throw new IllegalArgumentException("a v2 value needs its context");
            yield context;
          }
          default -> throw new IllegalArgumentException("not an encrypted value");
        };
    try {
      var data = Base64.getDecoder().decode(parts[2]);
      var nonce = new byte[CryptoConstants.NONCE_BYTES];
      System.arraycopy(data, 0, nonce, 0, nonce.length);
      var cipher = cipher(Cipher.DECRYPT_MODE, parts[1], nonce, bound);
      return new String(
          cipher.doFinal(data, nonce.length, data.length - nonce.length), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException(
          "the value was changed, the key is wrong, or it belongs elsewhere", e);
    }
  }

  /**
   * The associated data (NIST SP 800-38D): the key id for v1; the key id, a 0x00 and the context
   * for v2, so neither can be confused with the other.
   */
  private Cipher cipher(int mode, String keyId, byte[] nonce, @Nullable String context)
      throws GeneralSecurityException {
    var key = keys.get(keyId);
    if (key == null) throw new IllegalArgumentException("unknown key id " + keyId);
    var cipher = Cipher.getInstance(CryptoConstants.CIPHER);
    cipher.init(mode, key, new GCMParameterSpec(CryptoConstants.TAG_BITS, nonce));
    cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
    if (context != null) {
      cipher.updateAAD(new byte[] {CryptoConstants.CONTEXT_SEPARATOR});
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
    }
    return cipher;
  }

  private static SecretKey key(String id, String base64) {
    if (!id.matches(CryptoConstants.KEY_ID_PATTERN))
      throw new IllegalStateException("bad key id " + id);
    return new SecretKeySpec(decodeKey(id, base64), CryptoConstants.KEY_ALGORITHM);
  }

  private static byte[] decodeKey(String name, String base64) {
    var bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
    if (bytes.length != CryptoConstants.KEY_BYTES) {
      throw new IllegalStateException(
          name + " must be " + CryptoConstants.KEY_BYTES + " bytes, base64");
    }
    return bytes;
  }
}
