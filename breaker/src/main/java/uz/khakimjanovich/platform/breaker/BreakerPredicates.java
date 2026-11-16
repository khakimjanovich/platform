package uz.khakimjanovich.platform.breaker;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public final class BreakerPredicates {

  private BreakerPredicates() {}

  public static Predicate<Throwable> defaultPredicate() {
    return BreakerPredicates::isFailure;
  }

  public static boolean isFailure(Throwable ex) {
    var cause =
        ex.getCause() != null && !(ex instanceof RestClientResponseException) ? ex.getCause() : ex;
    if (cause instanceof RestClientResponseException rce) {
      return rce.getStatusCode().is5xxServerError();
    }
    return cause instanceof IOException
        || cause instanceof TimeoutException
        || cause instanceof ResourceAccessException;
  }
}
