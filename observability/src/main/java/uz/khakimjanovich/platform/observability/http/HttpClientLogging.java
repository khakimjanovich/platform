package uz.khakimjanovich.platform.observability.http;

import static uz.khakimjanovich.platform.observability.ObservabilityConstants.MAX_LOGGED_BYTES;
import static uz.khakimjanovich.platform.observability.ObservabilityConstants.NOT_LOGGED;
import static uz.khakimjanovich.platform.observability.ObservabilityConstants.NO_BODIES;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import uz.khakimjanovich.platform.masking.Masking;

public class HttpClientLogging implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(HttpClientLogging.class);

  private final Masking masking;
  private final HttpLoggingProperties properties;

  public HttpClientLogging(Masking masking) {
    this(
        masking,
        new HttpLoggingProperties(false, MAX_LOGGED_BYTES, List.of(), List.of(), List.of()));
  }

  public HttpClientLogging(Masking masking, HttpLoggingProperties properties) {
    this.masking = masking;
    this.properties = properties;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var call = request.getMethod() + " " + masking.uri(request.getURI());
    var headersString = formatHeaders(request.getHeaders());
    var callWithHeaders = headersString.isEmpty() ? call : call + " " + headersString;
    var bodies = !request.getAttributes().containsKey(NO_BODIES);
    log.info(
        "→ {} | {}",
        callWithHeaders,
        bodies ? masking.body(body, type(request.getHeaders())) : NOT_LOGGED);
    var started = System.nanoTime();
    ClientHttpResponse response;
    try {
      response = execution.execute(request, body);
    } catch (IOException e) {
      log.warn("✗ {} | {} ms | {}", call, millis(started), e.toString());
      throw e;
    }
    var headers = response.getHeaders();
    var length = headers.getContentLength();
    String logged;
    ClientHttpResponse answer = response;
    if (!bodies) {
      logged = NOT_LOGGED;
    } else if (!loggable(headers.getContentType()) || length > MAX_LOGGED_BYTES) {
      logged = size(length, headers);
    } else {
      // Read at most one byte past the cap: a longer body is put back in front of the rest.
      var start = response.getBody().readNBytes(MAX_LOGGED_BYTES + 1);
      if (start.length <= MAX_LOGGED_BYTES) {
        logged = masking.body(start, type(headers));
        answer = new Read(response, new ByteArrayInputStream(start));
      } else {
        logged = "<more than " + MAX_LOGGED_BYTES + " bytes, " + headers.getContentType() + ">";
        answer =
            new Read(
                response,
                new SequenceInputStream(new ByteArrayInputStream(start), response.getBody()));
      }
    }
    var status = response.getStatusCode();
    var line = "← {} {} | {} ms | {}";
    if (status.isError()) log.warn(line, status.value(), call, millis(started), logged);
    else log.info(line, status.value(), call, millis(started), logged);
    return answer;
  }

  private String formatHeaders(HttpHeaders headers) {
    if (properties.includeHeaders() == null || properties.includeHeaders().isEmpty()) {
      return "";
    }
    var joined = new StringBuilder();
    for (var entry : headers.headerNames()) {
      if (!properties.isHeaderIncluded(entry)) {
        continue;
      }
      for (var value : headers.getOrEmpty(entry)) {
        var maskedValue = masking.contains(entry) ? masking.maskValue(value) : value;
        if (!joined.isEmpty()) {
          joined.append(", ");
        }
        joined.append(entry).append(": ").append(maskedValue);
      }
    }
    return joined.isEmpty() ? "" : "[" + joined + "]";
  }

  private static boolean loggable(@Nullable MediaType type) {
    if (type == null) return false;
    var subtype = type.getSubtype().toLowerCase(Locale.ROOT);
    return subtype.contains("json")
        || subtype.contains("xml")
        || MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(type);
  }

  private static String size(long length, HttpHeaders headers) {
    var type = headers.getContentType();
    return "<" + (length < 0 ? "?" : length) + " bytes" + (type == null ? "" : ", " + type) + ">";
  }

  private static @Nullable String type(HttpHeaders headers) {
    var type = headers.getContentType();
    return type == null ? null : type.toString();
  }

  private static long millis(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  private record Read(ClientHttpResponse response, InputStream body) implements ClientHttpResponse {

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return response.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return response.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
      return response.getHeaders();
    }

    @Override
    public InputStream getBody() {
      return body;
    }

    @Override
    public void close() {
      response.close();
    }
  }
}
