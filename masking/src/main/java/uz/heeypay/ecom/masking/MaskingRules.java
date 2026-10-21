package uz.heeypay.ecom.masking;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Which field names {@link Masking} hides, compared ignoring case. A service adds its own names to
 * the defaults; it never repeats them.
 *
 * @param secretFields hidden whole: expiry dates, codes, passwords, secrets, keys, names
 * @param tokenFields cut to their last 4 characters: tokens, phones, accounts
 * @param cardFields card numbers, kept to their first 6 and last 4 digits
 */
public record MaskingRules(
    Set<String> secretFields, Set<String> tokenFields, Set<String> cardFields) {

  private static final MaskingRules DEFAULTS =
      new MaskingRules(
          Set.of(
              "expiry",
              "cvv",
              "cvc",
              "password",
              "client_secret",
              "clientsecret",
              "secret",
              "signingkey",
              "otp_code",
              "fullname",
              "cardholder"),
          Set.of(
              "token",
              "cardtoken",
              "processortoken",
              "access_token",
              "refresh_token",
              "id_token",
              "phone",
              "aacct",
              "account"),
          Set.of("pan", "cardnumber", "number"));

  public MaskingRules {
    secretFields = lowerCase(secretFields);
    tokenFields = lowerCase(tokenFields);
    cardFields = lowerCase(cardFields);
  }

  /** The card, token and secret names every service hides. */
  public static MaskingRules defaults() {
    return DEFAULTS;
  }

  /** These rules plus more names of each kind. */
  public MaskingRules with(
      Collection<String> moreSecretFields,
      Collection<String> moreTokenFields,
      Collection<String> moreCardFields) {
    return new MaskingRules(
        union(secretFields, moreSecretFields),
        union(tokenFields, moreTokenFields),
        union(cardFields, moreCardFields));
  }

  private static Set<String> union(Set<String> names, Collection<String> more) {
    return Stream.concat(names.stream(), more.stream()).collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> lowerCase(Set<String> names) {
    return names.stream()
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
