package uz.khakimjanovich.platform.masking;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MaskingRules(
    boolean maskCards,
    Set<String> secretFields,
    Set<String> tokenFields,
    Set<String> cardFields,
    Map<String, Set<String>> secretsBeside) {

  private static final MaskingRules DEFAULTS =
      new MaskingRules(
          true,
          MaskingConstants.DEFAULT_SECRET_FIELDS,
          MaskingConstants.DEFAULT_TOKEN_FIELDS,
          MaskingConstants.DEFAULT_CARD_FIELDS,
          MaskingConstants.DEFAULT_SECRETS_BESIDE);

  public MaskingRules(
      Set<String> secretFields,
      Set<String> tokenFields,
      Set<String> cardFields,
      Map<String, Set<String>> secretsBeside) {
    this(true, secretFields, tokenFields, cardFields, secretsBeside);
  }

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

  public static MaskingRules defaults() {
    return DEFAULTS;
  }

  public MaskingRules with(
      Collection<String> moreSecretFields,
      Collection<String> moreTokenFields,
      Collection<String> moreCardFields,
      Map<String, ? extends Collection<String>> moreSecretsBeside) {
    return with(maskCards, moreSecretFields, moreTokenFields, moreCardFields, moreSecretsBeside);
  }

  public MaskingRules with(
      boolean maskCards,
      Collection<String> moreSecretFields,
      Collection<String> moreTokenFields,
      Collection<String> moreCardFields,
      Map<String, ? extends Collection<String>> moreSecretsBeside) {
    var beside = new HashMap<String, Set<String>>(secretsBeside);
    moreSecretsBeside.forEach(
        (name, context) ->
            beside.merge(name.toLowerCase(Locale.ROOT), Set.copyOf(context), MaskingRules::union));
    return new MaskingRules(
        maskCards,
        union(secretFields, moreSecretFields),
        union(tokenFields, moreTokenFields),
        union(cardFields, moreCardFields),
        beside);
  }

  public MaskingRules withoutCards() {
    return new MaskingRules(false, secretFields, tokenFields, Set.of(), secretsBeside);
  }

  public MaskingRules withoutCvv() {
    var filtered =
        secretFields.stream()
            .filter(f -> !f.equals("cvv") && !f.equals("cvc"))
            .collect(Collectors.toUnmodifiableSet());
    return new MaskingRules(maskCards, filtered, tokenFields, cardFields, secretsBeside);
  }

  public MaskingRules withoutPhone() {
    var filtered =
        tokenFields.stream()
            .filter(f -> !f.equals("phone"))
            .collect(Collectors.toUnmodifiableSet());
    return new MaskingRules(maskCards, secretFields, filtered, cardFields, secretsBeside);
  }

  public MaskingRules withoutTokens() {
    var filtered =
        tokenFields.stream()
            .filter(
                f ->
                    !f.equals("token")
                        && !f.equals("access_token")
                        && !f.equals("refresh_token")
                        && !f.equals("id_token"))
            .collect(Collectors.toUnmodifiableSet());
    var secretFiltered =
        secretFields.stream()
            .filter(f -> !f.equals("authorization") && !f.equals("bearer"))
            .collect(Collectors.toUnmodifiableSet());
    return new MaskingRules(maskCards, secretFiltered, filtered, cardFields, secretsBeside);
  }

  public MaskingRules withoutPinfl() {
    var filtered =
        tokenFields.stream()
            .filter(f -> !f.equals("pinfl") && !f.equals("jshshir"))
            .collect(Collectors.toUnmodifiableSet());
    return new MaskingRules(maskCards, secretFields, filtered, cardFields, secretsBeside);
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
