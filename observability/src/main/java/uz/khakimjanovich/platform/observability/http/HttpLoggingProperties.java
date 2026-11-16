package uz.khakimjanovich.platform.observability.http;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

/**
 * Configuration properties for HTTP wire logging under platform.logging.http.
 *
 * @param enabled master switch for HTTP wire logging
 * @param maxBodyBytes maximum body size in bytes to log before truncating
 * @param includeHeaders list of HTTP header names to log (case-insensitive)
 * @param excludePaths ant-style URL path patterns to completely exclude from HTTP wire logging
 * @param noBodyPaths ant-style URL path patterns where request and response bodies are not logged
 */
@ConfigurationProperties("platform.logging.http")
public record HttpLoggingProperties(
    boolean enabled,
    int maxBodyBytes,
    @Nullable List<String> includeHeaders,
    @Nullable List<String> excludePaths,
    @Nullable List<String> noBodyPaths) {

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  public boolean isHeaderIncluded(String name) {
    if (includeHeaders == null || includeHeaders.isEmpty()) {
      return false;
    }
    for (var header : includeHeaders) {
      if (header.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  public boolean isExcluded(String path) {
    if (excludePaths == null || excludePaths.isEmpty()) {
      return false;
    }
    for (var pattern : excludePaths) {
      if (MATCHER.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  public boolean isNoBody(String path) {
    if (noBodyPaths == null || noBodyPaths.isEmpty()) {
      return false;
    }
    for (var pattern : noBodyPaths) {
      if (MATCHER.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }
}
