package uz.heeypay.ecom.error;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves as a problem detail with a stable {@code code}: a {@link GeneralException}
 * from its definition (status, title, message in the caller's language), Spring's own errors with a
 * code named after their status. Invalid input lists the failing fields. Anything unexpected
 * becomes a plain 500 with a code, never a stack trace.
 *
 * <p>Every error response is logged once, with its request: a 5xx as an error, one whose definition
 * says {@code is-loggable} as a warning, anything else (a caller's mistake) as info.
 */
@RestControllerAdvice
@RequiredArgsConstructor
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Log log = LogFactory.getLog(ProblemDetailsAdvice.class);

  private final ErrorStarterProperties errors;

  @ExceptionHandler(GeneralException.class)
  ResponseEntity<ProblemDetail> general(GeneralException ex) {
    var problem = problem(ex.getStatus(), ex.getParams());
    if (!ex.getSubErrors().isEmpty()) {
      problem.setProperty(ErrorConstants.SUB_ERRORS_PROPERTY, ex.getSubErrors());
    }
    var definition = errors.error().get(ex.getStatus().getType());
    log(problem, definition != null && definition.isLoggable());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception ex) {
    log.error(ErrorConstants.LOG_UNEXPECTED_ERROR, ex);
    return general(new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR));
  }

  private ProblemDetail problem(StatusThrowable status, Object... params) {
    var definition = errors.error().get(status.getType());
    if (definition == null) {
      log.error(ErrorConstants.LOG_NO_DEFINITION.replace("{}", status.getType()));
      status = ErrorExceptionStatus.INTERNAL_ERROR;
      params = new Object[0];
      definition = errors.error().getOrDefault(status.getType(), ErrorConstants.LAST_RESORT);
    }
    var detail = definition.message(LocaleContextHolder.getLocale(), params);
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(definition.httpCode()), detail);
    problem.setTitle(definition.title());
    problem.setProperty(ErrorConstants.CODE_PROPERTY, status.getType());
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(ProblemDetailsAdvice::fieldError)
            .toList();
    return invalid(ex, ex.getBody(), errors, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                Map.of(
                                    ErrorConstants.FIELD,
                                    String.valueOf(result.getMethodParameter().getParameterName()),
                                    ErrorConstants.MESSAGE,
                                    String.valueOf(error.getDefaultMessage()))))
            .toList();
    return invalid(ex, ex.getBody(), errors, headers, status, request);
  }

  /**
   * Every problem leaves with a code: its own, or one named after its HTTP status (e.g.
   * METHOD_NOT_ALLOWED).
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body == null && ex instanceof ErrorResponse error) {
      // Spring passes no body for its own errors; fill and translate it as Spring would.
      body = error.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
    }
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null
            || !problem.getProperties().containsKey(ErrorConstants.CODE_PROPERTY))) {
      var named = HttpStatus.resolve(status.value());
      problem.setProperty(
          ErrorConstants.CODE_PROPERTY,
          named == null ? String.valueOf(status.value()) : named.name());
    }
    if (body instanceof ProblemDetail problem) log(problem, false);
    return super.handleExceptionInternal(ex, body, headers, status, request);
  }

  private ResponseEntity<Object> invalid(
      Exception ex,
      ProblemDetail problem,
      List<Map<String, String>> errors,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var invalid = problem(ErrorExceptionStatus.VALIDATION_FAILED);
    problem.setTitle(invalid.getTitle());
    problem.setDetail(invalid.getDetail());
    problem.setProperty(
        ErrorConstants.CODE_PROPERTY, ErrorExceptionStatus.VALIDATION_FAILED.getType());
    problem.setProperty(ErrorConstants.ERRORS_PROPERTY, errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static Map<String, String> fieldError(FieldError error) {
    return Map.of(
        ErrorConstants.FIELD,
        error.getField(),
        ErrorConstants.MESSAGE,
        String.valueOf(error.getDefaultMessage()));
  }

  /** One line per error response; invalid input names its fields. */
  private static void log(ProblemDetail problem, boolean loggable) {
    var properties = problem.getProperties() == null ? Map.of() : problem.getProperties();
    var detail =
        properties.containsKey(ErrorConstants.ERRORS_PROPERTY)
            ? problem.getDetail() + " " + properties.get(ErrorConstants.ERRORS_PROPERTY)
            : problem.getDetail();
    var line =
        ErrorConstants.LOG_PROBLEM.formatted(
            problem.getStatus(), properties.get(ErrorConstants.CODE_PROPERTY), request(), detail);
    if (problem.getStatus() >= 500) log.error(line);
    else if (loggable) log.warn(line);
    else log.info(line);
  }

  /** "GET /api/v1/…" for the request being answered, or "-" outside one. */
  private static String request() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes current
        ? current.getRequest().getMethod() + " " + current.getRequest().getRequestURI()
        : "-";
  }
}
