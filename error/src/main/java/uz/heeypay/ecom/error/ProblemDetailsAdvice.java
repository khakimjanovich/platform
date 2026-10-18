package uz.heeypay.ecom.error;

import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring's own handler turns every {@link ApiException} and every framework error into a problem
 * detail; here each one also gets a stable {@code code}. Invalid input lists the failing fields.
 * Anything unexpected becomes a plain 500 with a code, never a stack trace.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Log log = LogFactory.getLog(ProblemDetailsAdvice.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex) {
    log.error(ErrorConstantUtils.LOG_UNEXPECTED_ERROR, ex);
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, ErrorConstantUtils.INTERNAL_ERROR_DETAIL);
    problem.setProperty(ErrorConstantUtils.CODE_PROPERTY, ErrorConstantUtils.INTERNAL_ERROR_CODE);
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
                                    ErrorConstantUtils.FIELD,
                                    String.valueOf(result.getMethodParameter().getParameterName()),
                                    ErrorConstantUtils.MESSAGE,
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
      body = error.getBody(); // Spring passes no body for its own errors and fills it in later
    }
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null
            || !problem.getProperties().containsKey(ErrorConstantUtils.CODE_PROPERTY))) {
      var named = HttpStatus.resolve(status.value());
      problem.setProperty(
          ErrorConstantUtils.CODE_PROPERTY,
          named == null ? String.valueOf(status.value()) : named.name());
    }
    return super.handleExceptionInternal(ex, body, headers, status, request);
  }

  private ResponseEntity<Object> invalid(
      Exception ex,
      ProblemDetail problem,
      List<Map<String, String>> errors,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    problem.setDetail(ErrorConstantUtils.VALIDATION_DETAIL);
    problem.setProperty(ErrorConstantUtils.CODE_PROPERTY, ErrorConstantUtils.VALIDATION_CODE);
    problem.setProperty(ErrorConstantUtils.ERRORS_PROPERTY, errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static Map<String, String> fieldError(FieldError error) {
    return Map.of(
        ErrorConstantUtils.FIELD,
        error.getField(),
        ErrorConstantUtils.MESSAGE,
        String.valueOf(error.getDefaultMessage()));
  }
}
