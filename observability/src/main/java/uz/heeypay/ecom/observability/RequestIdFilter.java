package uz.heeypay.ecom.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every response names its request: the trace id, the same one on every log line the request wrote,
 * in this service and in every service it called. A caller quotes it to support.
 */
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final ObjectProvider<Tracer> tracer;

  public RequestIdFilter(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var current = tracer.getIfAvailable();
    var span = current == null ? null : current.currentSpan();
    if (span != null) response.setHeader(REQUEST_ID_HEADER, span.context().traceId());
    chain.doFilter(request, response);
  }
}
