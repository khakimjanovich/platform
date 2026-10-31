package uz.khakimjanovich.platform.masking;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
 * @param secretsBeside in JSON, a field hidden whole only where its object also has, or sits under,
 *     one of the given names (a {@code value} beside a {@code type}); elsewhere it's shown
 */
public record MaskingRules(
    Set<String> secretFields,
    Set<String> tokenFields,
    Set<String> cardFields,
    Map<String, Set<String>> secretsBeside) {

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
              "fullname",
              "cardholder"),
          Set.of("token", "access_token", "refresh_token", "id_token", "phone", "account"),
          Set.of("pan", "cardnumber", "number"),
          // A typed value, as Keycloak's credentials and client secrets are {"type", "value"}.
          Map.of("value", Set.of("type")));

  public MaskingRules {
    secretFields = lowerCase(secretFields);
    tokenFields = lowerCase(tokenFields);
    cardFields = lowerCase(cardFields);
    secretsBeside =
        secretsBeside.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().toLowerCase(Locale.ROOT),
                    entry -> lowerCase(entry.getValue()),
                    MaskingRules::union));
  }

  /** The generic card, token and secret names; a service adds its own. */
  public static MaskingRules defaults() {
    return DEFAULTS;
  }

  /** These rules plus more names of each kind. */
  public MaskingRules with(
      Collection<String> moreSecretFields,
      Collection<String> moreTokenFields,
      Collection<String> moreCardFields,
      Map<String, ? extends Collection<String>> moreSecretsBeside) {
    var beside = new HashMap<String, Set<String>>(secretsBeside);
    moreSecretsBeside.forEach(
        (name, context) ->
            beside.merge(name.toLowerCase(Locale.ROOT), Set.copyOf(context), MaskingRules::union));
    return new MaskingRules(
        union(secretFields, moreSecretFields),
        union(tokenFields, moreTokenFields),
        union(cardFields, moreCardFields),
        beside);
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
