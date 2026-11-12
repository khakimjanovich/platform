package uz.khakimjanovich.platform.error;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

public final class Problems {

  private static final JsonMapper JSON =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  private Problems() {}

  public static Optional<ProblemDetail> of(RestClientResponseException e) {
    try {
      return Optional.ofNullable(e.getResponseBodyAs(ProblemDetail.class));
    } catch (IllegalStateException noConverters) {
      return read(e.getResponseBodyAsByteArray());
    } catch (RuntimeException notAProblem) {
      return Optional.empty();
    }
  }

  private static Optional<ProblemDetail> read(byte[] body) {
    try {
      return body.length == 0
          ? Optional.empty()
          : Optional.of(JSON.readValue(body, ProblemDetail.class));
    } catch (RuntimeException notAProblem) {
      return Optional.empty();
    }
  }

  public static @Nullable String code(ProblemDetail problem) {
    return problem.getProperties() == null
            || !(problem.getProperties().get(ErrorConstants.CODE_PROPERTY) instanceof String code)
        ? null
        : code;
  }

  public static List<GeneralException.SubError> subErrors(ProblemDetail problem) {
    if (problem.getProperties() == null
        || !(problem.getProperties().get(ErrorConstants.SUB_ERRORS_PROPERTY)
            instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(
            entry ->
                new GeneralException.SubError(
                    text(entry.get(ErrorConstants.CODE_PROPERTY)),
                    text(entry.get(ErrorConstants.MESSAGE))))
        .toList();
  }

  private static @Nullable String text(@Nullable Object value) {
    return value == null ? null : value.toString();
  }
}
