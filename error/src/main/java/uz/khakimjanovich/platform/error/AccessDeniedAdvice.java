package uz.khakimjanovich.platform.error;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnClass(name = ErrorConstants.ACCESS_DENIED)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class AccessDeniedAdvice {

  private final ProblemDetailsAdvice problems;

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex) {
    return problems.general(new GeneralException(ErrorExceptionStatus.FORBIDDEN));
  }
}
