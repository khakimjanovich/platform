package uz.khakimjanovich.platform.buildlogic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * The newest build of a snapshot in the registry as its unique version
 * ({@code 2.8.0-20261117.152915-1}), read from the version's maven-metadata.xml; null when the
 * registry can't say. Gradle caches a {@code -SNAPSHOT} it resolves in settings (the catalog, the
 * plugins) for 24 hours; a unique version never changes, so a newer snapshot is a new version to it.
 * As a ValueSource it is read on every build, also when the configuration cache is reused.
 */
public abstract class LatestSnapshot implements ValueSource<String, LatestSnapshot.Parameters> {

  private static final Pattern TIMESTAMP = Pattern.compile("<timestamp>([^<]+)</timestamp>");
  private static final Pattern BUILD_NUMBER = Pattern.compile("<buildNumber>([^<]+)</buildNumber>");

  public interface Parameters extends ValueSourceParameters {
    /** The registry's base URL. */
    Property<String> getRegistry();

    /** The artifact in the platform's group, e.g. versioning. */
    Property<String> getArtifact();

    /** The snapshot version, e.g. 2.8.0-SNAPSHOT. */
    Property<String> getVersion();

    Property<String> getUser();

    Property<String> getToken();
  }

  @Override
  public @Nullable String obtain() {
    var parameters = getParameters();
    var version = parameters.getVersion().get();
    var url =
        URI.create(
            parameters.getRegistry().get().replaceAll("/+$", "")
                + "/"
                + PlatformSettingsPlugin.GROUP.replace('.', '/')
                + "/"
                + parameters.getArtifact().get()
                + "/"
                + version
                + "/maven-metadata.xml");
    var credentials = parameters.getUser().get() + ":" + parameters.getToken().get();
    var request =
        HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(10))
            .header(
                "Authorization",
                "Basic "
                    + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
            .build();
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return unknown(url, "HTTP " + response.statusCode());
      }
      var timestamp = TIMESTAMP.matcher(response.body());
      var buildNumber = BUILD_NUMBER.matcher(response.body());
      if (!timestamp.find() || !buildNumber.find()) {
        return unknown(url, "no <timestamp> and <buildNumber>");
      }
      return version.replace("-SNAPSHOT", "-" + timestamp.group(1) + "-" + buildNumber.group(1));
    } catch (IOException e) {
      return unknown(url, e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return unknown(url, "interrupted");
    }
  }

  private static @Nullable String unknown(URI url, String why) {
    Logging.getLogger(LatestSnapshot.class)
        .warn("platform: newest snapshot unknown ({}: {}); Gradle's cached one is used", url, why);
    return null;
  }
}
