package uz.khakimjanovich.platform.observability.http;

import static uz.khakimjanovich.platform.observability.ObservabilityConstants.NOT_LOGGED;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import uz.khakimjanovich.platform.masking.Masking;

public class HttpServerLoggingFilter extends OncePerRequestFilter implements Ordered {

  private static final Logger log = LoggerFactory.getLogger(HttpServerLoggingFilter.class);

  private final Masking masking;
  private final HttpLoggingProperties properties;

  public HttpServerLoggingFilter(Masking masking, HttpLoggingProperties properties) {
    this.masking = masking;
    this.properties = properties;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 3;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return properties.isExcluded(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var uri = request.getRequestURI();
    var query = request.getQueryString();
    var whole = query == null ? uri : uri + "?" + query;
    String maskedUri;
    try {
      maskedUri = masking.uri(URI.create(whole));
    } catch (IllegalArgumentException e) {
      maskedUri = whole;
    }

    var call = request.getMethod() + " " + maskedUri;
    var headersString = formatHeaders(request);
    var callWithHeaders = headersString.isEmpty() ? call : call + " " + headersString;

    var noBody = properties.isNoBody(uri);
    HttpServletRequest requestToUse = request;
    String requestBody;

    if (noBody) {
      requestBody = NOT_LOGGED;
    } else if (isMultipart(request)) {
      requestBody = "<multipart/form-data>";
    } else if (!isLoggable(request.getContentType())) {
      requestBody = size(request.getContentLengthLong(), request.getContentType());
    } else {
      var raw = request.getInputStream().readAllBytes();
      requestToUse = new CachedRequest(request, raw);
      if (raw.length > properties.maxBodyBytes()) {
        requestBody =
            "<more than " + properties.maxBodyBytes() + " bytes, " + request.getContentType() + ">";
      } else {
        requestBody = masking.body(raw, request.getContentType());
      }
    }

    log.info("→ {} | {}", callWithHeaders, requestBody);

    var responseWrapper = new ContentCachingResponseWrapper(response);
    var started = System.nanoTime();
    try {
      chain.doFilter(requestToUse, responseWrapper);
    } catch (Exception e) {
      log.warn("✗ {} | {} ms | {}", call, millis(started), e.toString());
      throw e;
    } finally {
      var status = responseWrapper.getStatus();
      String responseBody;
      if (noBody) {
        responseBody = NOT_LOGGED;
      } else if (!isLoggable(responseWrapper.getContentType())) {
        responseBody = size(responseWrapper.getContentSize(), responseWrapper.getContentType());
      } else {
        var rawResponse = responseWrapper.getContentAsByteArray();
        if (rawResponse.length > properties.maxBodyBytes()) {
          responseBody =
              "<more than "
                  + properties.maxBodyBytes()
                  + " bytes, "
                  + responseWrapper.getContentType()
                  + ">";
        } else {
          responseBody = masking.body(rawResponse, responseWrapper.getContentType());
        }
      }

      var line = "← {} {} | {} ms | {}";
      if (status >= 400) {
        log.warn(line, status, call, millis(started), responseBody);
      } else {
        log.info(line, status, call, millis(started), responseBody);
      }
      responseWrapper.copyBodyToResponse();
    }
  }

  private String formatHeaders(HttpServletRequest request) {
    if (properties.includeHeaders() == null || properties.includeHeaders().isEmpty()) {
      return "";
    }
    var joined = new StringBuilder();
    for (var names = request.getHeaderNames(); names != null && names.hasMoreElements(); ) {
      var name = names.nextElement();
      if (!properties.isHeaderIncluded(name)) {
        continue;
      }
      for (var values = request.getHeaders(name); values != null && values.hasMoreElements(); ) {
        var value = values.nextElement();
        if (masking.contains(name)) {
          value = masking.maskValue(value);
        }
        if (!joined.isEmpty()) {
          joined.append(", ");
        }
        joined.append(name).append(": ").append(value);
      }
    }
    return joined.isEmpty() ? "" : "[" + joined + "]";
  }

  private static boolean isLoggable(@Nullable String contentType) {
    if (contentType == null) {
      return true;
    }
    var lower = contentType.toLowerCase(Locale.ROOT);
    return lower.contains("json")
        || lower.contains("xml")
        || lower.contains("text")
        || lower.contains("x-www-form-urlencoded");
  }

  private static boolean isMultipart(HttpServletRequest request) {
    var type = request.getContentType();
    return type != null && type.toLowerCase(Locale.ROOT).contains("multipart");
  }

  private static String size(long length, @Nullable String contentType) {
    return "<"
        + (length < 0 ? "?" : length)
        + " bytes"
        + (contentType == null ? "" : ", " + contentType)
        + ">";
  }

  private static long millis(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  private static class CachedRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      var stream = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return stream.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
          return stream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return stream.read(b, off, len);
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      var encoding = getCharacterEncoding();
      var charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
