package uz.khakimjanovich.platform.observability;

import static uz.khakimjanovich.platform.observability.ObservabilityConstants.REQUEST_ID_HEADER;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter implements Ordered {

  private final ObjectProvider<Tracer> tracer;

  public RequestIdFilter(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
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
