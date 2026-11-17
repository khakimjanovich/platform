package uz.khakimjanovich.platform.vault;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.masking.Masking;

@Slf4j
public class VaultTransit {

  private final VaultTransitOperations transit;
  private final Masking masking;

  public VaultTransit(VaultOperations vault, String mount) {
    this(vault, mount, Masking.defaults());
  }

  public VaultTransit(VaultOperations vault, String mount, Masking masking) {
    this.transit = vault.opsForTransit(mount);
    this.masking = Objects.requireNonNull(masking, "masking must not be null");
  }

  public String encrypt(String key, String context, String plain) {
    return vault(
        () ->
            transit
                .encrypt(
                    key,
                    Plaintext.of(plain.getBytes(StandardCharsets.UTF_8)).with(context(context)))
                .getCiphertext());
  }

  public String decrypt(String key, String context, String sealed) {
    return vault(
        () -> transit.decrypt(key, Ciphertext.of(sealed).with(context(context))).asString());
  }

  public String rewrap(String key, String context, String sealed) {
    return vault(() -> transit.rewrap(key, sealed, context(context)));
  }

  public String latestPrefix(String key) {
    var found = vault(() -> transit.getKey(key));
    if (found == null) {
      log.warn(VaultConstants.TRANSIT_KEY_MISSING, key);
      throw new GeneralException(VaultExceptionStatus.VAULT_TRANSIT_REFUSED);
    }
    return VaultConstants.TRANSIT_PREFIX_FORMAT.formatted(found.getLatestVersion());
  }

  public String hmac(String key, int keyVersion, String value) {
    return vault(
        () ->
            transit
                .getHmac(
                    key,
                    VaultHmacRequest.builder()
                        .plaintext(Plaintext.of(value.getBytes(StandardCharsets.UTF_8)))
                        .keyVersion(keyVersion)
                        .build())
                .getHmac());
  }

  private static VaultTransitContext context(String context) {
    return VaultTransitContext.builder().context(context.getBytes(StandardCharsets.UTF_8)).build();
  }

  private <T extends @Nullable Object> T vault(Supplier<T> call) {
    try {
      return call.get();
    } catch (VaultException | RestClientException e) { // Vault refused, or didn't answer at all
      var sanitized = sanitize(e.getMessage());
      log.warn(VaultConstants.TRANSIT_FAILED, e.getClass().getSimpleName(), sanitized);
      throw new GeneralException(
          refused(e)
              ? VaultExceptionStatus.VAULT_TRANSIT_REFUSED
              : VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE);
    }
  }

  private String sanitize(@Nullable String rawMessage) {
    if (rawMessage == null || rawMessage.isBlank()) {
      return "";
    }
    var maskedByRules = masking.text(rawMessage);
    var text = maskedByRules == null ? rawMessage : maskedByRules;
    return VaultConstants.CARD_PATTERN
        .matcher(text)
        .replaceAll(
            match -> {
              var digits = match.group();
              if (digits.length() < 10) {
                return ".".repeat(digits.length());
              }
              return digits.substring(0, 6)
                  + ".".repeat(digits.length() - 10)
                  + digits.substring(digits.length() - 4);
            });
  }

  private static boolean refused(Exception e) {
    var cause = e instanceof HttpClientErrorException ? e : e.getCause();
    return cause instanceof HttpClientErrorException client
        && VaultConstants.TRANSIT_REFUSED_STATUSES.contains(client.getStatusCode().value());
  }
}
