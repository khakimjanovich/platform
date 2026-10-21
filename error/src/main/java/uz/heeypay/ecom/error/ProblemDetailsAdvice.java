package uz.heeypay.ecom.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
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
import uz.heeypay.ecom.masking.Masking;

/**
 * Every error leaves as a problem detail with a stable {@code code}: a {@link GeneralException}
 * from its definition (status, title, message in the caller's language), Spring's own errors with a
 * code named after their status. Invalid input lists the failing fields, whether a controller or a
 * {@code @Validated} service refused it. Anything unexpected becomes a plain 500 with a code, never
 * a stack trace. Every problem names the request it answers ({@code instance}) and, when the
 * request is traced, its {@code requestId}; a limit that lifts by itself sends {@code Retry-After}.
 *
 * <p>Every error response is logged once, with its request: a 5xx as an error, one whose definition
 * says {@code is-loggable} as a warning, anything else (a caller's mistake) as info. The line holds
 * the English message without its parameters, masked, so no caller's data reaches the log.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Log log = LogFactory.getLog(ProblemDetailsAdvice.class);

  private final ErrorStarterProperties errors;
  private final Masking masking = Masking.defaults();

  /** Needs only the definitions, so an app that scans this package gets a working one too. */
  ProblemDetailsAdvice(ErrorStarterProperties errors) {
    this.errors = errors;
  }

  @ExceptionHandler(GeneralException.class)
  ResponseEntity<ProblemDetail> general(GeneralException ex) {
    var definition = errors.error().get(ex.getStatus().getType());
    var problem = problem(ex.getStatus(), ex.getParams());
    if (!ex.getSubErrors().isEmpty()) {
      problem.setProperty(ErrorConstants.SUB_ERRORS_PROPERTY, ex.getSubErrors());
    }
    log(problem, definition == null ? null : definition.messageEn(), loggable(definition));
    var response =
        ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (ex.getRetryAfter() != null) {
      // Whole seconds, rounded up: a client that waits this long is never early.
      var seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
      response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
    }
    return response.body(problem);
  }

  /** Method validation on a {@code @Validated} bean: the same 400 as a controller's. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException ex) {
    var problem = problem(ErrorExceptionStatus.VALIDATION_FAILED);
    var fields =
        ex.getConstraintViolations().stream()
            .map(
                violation ->
                    Map.of(
                        ErrorConstants.FIELD,
                        violation.getPropertyPath().toString(),
                        ErrorConstants.MESSAGE,
                        violation.getMessage()))
            .toList();
    problem.setProperty(ErrorConstants.ERRORS_PROPERTY, fields);
    log(problem, ErrorConstants.LOG_INVALID + fieldNames(fields), false);
    return ResponseEntity.badRequest()
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
    identify(problem);
    return problem;
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
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
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
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
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (body == null && ex instanceof ErrorResponse error) {
      // Spring passes no body for its own errors; fill and translate it as Spring would.
      body = error.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
    }
    if (body instanceof ProblemDetail problem) {
      var named = HttpStatus.resolve(status.value());
      if (problem.getProperties() == null
          || !problem.getProperties().containsKey(ErrorConstants.CODE_PROPERTY)) {
        problem.setProperty(
            ErrorConstants.CODE_PROPERTY,
            named == null ? String.valueOf(status.value()) : named.name());
        log(problem, ex.getMessage(), false);
      }
      identify(problem);
    }
    return super.handleExceptionInternal(ex, body, headers, status, request);
  }

  private @Nullable ResponseEntity<Object> invalid(
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
    log(problem, ErrorConstants.LOG_INVALID + fieldNames(errors), false);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static Map<String, String> fieldError(FieldError error) {
    return Map.of(
        ErrorConstants.FIELD,
        error.getField(),
        ErrorConstants.MESSAGE,
        String.valueOf(error.getDefaultMessage()));
  }

  private static List<String> fieldNames(List<Map<String, String>> errors) {
    return errors.stream().map(error -> error.get(ErrorConstants.FIELD)).toList();
  }

  private static boolean loggable(ErrorStarterProperties.@Nullable ErrorDefinition definition) {
    return definition != null && definition.isLoggable();
  }

  /** RFC 9457 §3.1.5: the request this problem answers, and its trace id when it is traced. */
  private static void identify(ProblemDetail problem) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes current) {
      problem.setInstance(URI.create(current.getRequest().getRequestURI()));
    }
    var traceId = MDC.get(ErrorConstants.TRACE_ID_MDC);
    if (traceId != null && !traceId.isBlank()) {
      problem.setProperty(ErrorConstants.REQUEST_ID_PROPERTY, traceId);
    }
  }

  /** One line per error response: status, code, request, and the English reason, masked. */
  private void log(ProblemDetail problem, @Nullable String reason, boolean loggable) {
    var properties = problem.getProperties() == null ? Map.of() : problem.getProperties();
    var line =
        ErrorConstants.LOG_PROBLEM.formatted(
            problem.getStatus(),
            properties.get(ErrorConstants.CODE_PROPERTY),
            request(),
            masking.text(reason == null ? String.valueOf(problem.getTitle()) : reason));
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
