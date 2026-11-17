package uz.khakimjanovich.platform.masking;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record MaskingRules(String maskChar, Set<String> fields) {

  public MaskingRules {
    maskChar =
        (maskChar == null || maskChar.isBlank()) ? MaskingConstants.DEFAULT_MASK_CHAR : maskChar;
    fields =
        fields.stream()
            .map(f -> f.toLowerCase(Locale.ROOT).strip())
            .filter(f -> !f.isBlank())
            .collect(Collectors.toUnmodifiableSet());
  }

  public static MaskingRules empty() {
    return new MaskingRules(MaskingConstants.DEFAULT_MASK_CHAR, Set.of());
  }

  public static MaskingRules of(Collection<String> fields) {
    return new MaskingRules(MaskingConstants.DEFAULT_MASK_CHAR, Set.copyOf(fields));
  }

  public static MaskingRules of(String maskChar, Collection<String> fields) {
    return new MaskingRules(maskChar, Set.copyOf(fields));
  }

  public boolean isEmpty() {
    return fields.isEmpty();
  }

  public boolean contains(String fieldName) {
    var lower = fieldName.toLowerCase(Locale.ROOT);
    if (fields.contains(lower)) {
      return true;
    }
    var dot = lower.lastIndexOf('.');
    return dot >= 0 && fields.contains(lower.substring(dot + 1));
  }
}
