package uz.heeypay.ecom.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM: every value gets a fresh 12-byte nonce, and the key id is authenticated with it, so
 * a ciphertext can't be moved to another key unnoticed. A tampered value fails to decrypt.
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
            CryptoConstantUtils.BLIND_INDEX_ALGORITHM);
  }

  public String encrypt(String plain) {
    try {
      var nonce = new byte[CryptoConstantUtils.NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      var cipher = cipher(Cipher.ENCRYPT_MODE, activeKeyId, nonce);
      var sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      var out = new byte[nonce.length + sealed.length];
      System.arraycopy(nonce, 0, out, 0, nonce.length);
      System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
      return String.join(
          CryptoConstantUtils.SEPARATOR,
          CryptoConstantUtils.VERSION,
          activeKeyId,
          Base64.getEncoder().encodeToString(out));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("encryption failed", e);
    }
  }

  public String decrypt(String envelope) {
    var parts = envelope.split(CryptoConstantUtils.SEPARATOR, 3);
    if (parts.length != 3 || !CryptoConstantUtils.VERSION.equals(parts[0])) {
      throw new IllegalArgumentException("not an encrypted value");
    }
    try {
      var data = Base64.getDecoder().decode(parts[2]);
      var nonce = new byte[CryptoConstantUtils.NONCE_BYTES];
      System.arraycopy(data, 0, nonce, 0, nonce.length);
      var cipher = cipher(Cipher.DECRYPT_MODE, parts[1], nonce);
      return new String(
          cipher.doFinal(data, nonce.length, data.length - nonce.length), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("the value was changed or the key is wrong", e);
    }
  }

  /** Whether the value was sealed with a key other than the active one, and should be rewrapped. */
  public boolean isStale(String envelope) {
    return !envelope.startsWith(
        CryptoConstantUtils.VERSION
            + CryptoConstantUtils.SEPARATOR
            + activeKeyId
            + CryptoConstantUtils.SEPARATOR);
  }

  /** Re-encrypts under the active key, for key rotation. */
  public String rewrap(String envelope) {
    return isStale(envelope) ? encrypt(decrypt(envelope)) : envelope;
  }

  /**
   * The same input always gives the same index, so equal values can be found without decrypting.
   */
  public String blindIndex(String plain) {
    try {
      var mac = Mac.getInstance(CryptoConstantUtils.BLIND_INDEX_ALGORITHM);
      mac.init(blindIndexKey);
      return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("blind index failed", e);
    }
  }

  private Cipher cipher(int mode, String keyId, byte[] nonce) throws GeneralSecurityException {
    var key = keys.get(keyId);
    if (key == null) throw new IllegalArgumentException("unknown key id " + keyId);
    var cipher = Cipher.getInstance(CryptoConstantUtils.CIPHER);
    cipher.init(mode, key, new GCMParameterSpec(CryptoConstantUtils.TAG_BITS, nonce));
    cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
    return cipher;
  }

  private static SecretKey key(String id, String base64) {
    if (!id.matches(CryptoConstantUtils.KEY_ID_PATTERN))
      throw new IllegalStateException("bad key id " + id);
    return new SecretKeySpec(decodeKey(id, base64), CryptoConstantUtils.KEY_ALGORITHM);
  }

  private static byte[] decodeKey(String name, String base64) {
    var bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
    if (bytes.length != CryptoConstantUtils.KEY_BYTES) {
      throw new IllegalStateException(
          name + " must be " + CryptoConstantUtils.KEY_BYTES + " bytes, base64");
    }
    return bytes;
  }
}
