package uz.khakimjanovich.gradle.settings;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.ProviderFactory;

/**
 * Where a build on the platform takes it from. {@code platformVersion} is a hub release (1.0.0).
 * {@code platformSource} is {@code release} (that release from the registry, releases only) or
 * {@code local} (its line, 1.x-SNAPSHOT, from ~/.m2 after {@code ./gradlew publishToMavenLocal} in
 * the hub and the libraries). The plugin sets the platform's repositories and imports its catalog as
 * {@code libs}.
 */
public final class PlatformSettingsPlugin implements Plugin<Settings> {

  static final String GROUP = "uz.khakimjanovich";
  // The hub's artifacts, the libraries (uz.khakimjanovich:config4j) and the plugin markers
  // (uz.khakimjanovich.java).
  static final String GROUPS = "uz\\.khakimjanovich(\\..+)?";
  static final String REGISTRY = "https://maven.pkg.github.com/khakimjanovich/platform";
  private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.\\d+\\.\\d+");

  enum Source {
    LOCAL,
    RELEASE
  }

  record Registry(URI url, String user, String token) {
    boolean http() {
      return url.getScheme().startsWith("http"); // a file: registry (the tests) takes no credentials
    }
  }

  @Override
  public void apply(Settings settings) {
    var providers = settings.getProviders();
    var source = source(providers);
    var version = version(providers, source);
    var registry =
        new Registry(
            URI.create(read(providers, "platformRegistry", "PLATFORM_REGISTRY", REGISTRY)),
            read(providers, "gpr.user", "GH_PACKAGES_ACTOR", ""),
            read(providers, "gpr.key", "GH_PACKAGES_TOKEN", ""));

    repositories(settings.getPluginManagement().getRepositories(), source, registry);
    var resolution = settings.getDependencyResolutionManagement();
    resolution.getRepositoriesMode().set(RepositoriesMode.PREFER_SETTINGS);
    repositories(resolution.getRepositories(), source, registry);
    resolution.getRepositories().mavenCentral();

    resolution
        .getVersionCatalogs()
        .create("libs", libs -> libs.from(GROUP + ":version-catalog:" + version));
  }

  // The platform's artifacts come only from these repositories (exclusiveContent): a mavenLocal()
  // that an init script adds can't stand in for a release, and the registry hands out releases only.
  private static void repositories(
      RepositoryHandler repositories, Source source, Registry registry) {
    var platform = new ArrayList<ArtifactRepository>();
    if (source == Source.LOCAL) {
      platform.add(
          repositories.mavenLocal(
              local -> {
                local.setName("platformLocal");
                local.mavenContent(content -> content.snapshotsOnly());
              }));
    }
    platform.add(
        repositories.maven(
            remote -> {
              remote.setName("platformRegistry");
              remote.setUrl(registry.url());
              if (registry.http()) {
                remote.credentials(
                    credentials -> {
                      credentials.setUsername(registry.user());
                      credentials.setPassword(registry.token());
                    });
              }
              remote.mavenContent(content -> content.releasesOnly());
            }));
    repositories.exclusiveContent(
        exclusive ->
            exclusive
                .forRepositories(platform.toArray(ArtifactRepository[]::new))
                .filter(content -> content.includeGroupByRegex(GROUPS)));
  }

  private static Source source(ProviderFactory providers) {
    var value = read(providers, "platformSource", "PLATFORM_SOURCE", "");
    try {
      return Source.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new GradleException(
          "platformSource is '"
              + value
              + "': set it to one of "
              + Arrays.toString(Source.values()).toLowerCase(Locale.ROOT)
              + " (gradle.properties, -PplatformSource or PLATFORM_SOURCE)",
          e);
    }
  }

  // release: the version as written; local: its line, 1.0.0 -> 1.x-SNAPSHOT.
  private static String version(ProviderFactory providers, Source source) {
    var release = read(providers, "platformVersion", "PLATFORM_VERSION", "");
    if (release.isEmpty()) {
      throw new GradleException(
          "platformVersion is not set: the platform release this build uses, e.g. 1.0.0");
    }
    var matcher = RELEASE.matcher(release);
    if (!matcher.matches()) {
      throw new GradleException(
          "platformVersion is " + release + ": write a release, X.Y.Z (e.g. 1.0.0)");
    }
    return source == Source.RELEASE ? release : matcher.group(1) + ".x-SNAPSHOT";
  }

  // The variable wins: CI sets PLATFORM_SOURCE=release on a tag over what a repository keeps in
  // gradle.properties, as its settings.gradle does for this plugin's own version.
  private static String read(
      ProviderFactory providers, String property, String variable, String fallback) {
    return providers
        .environmentVariable(variable)
        .orElse(providers.gradleProperty(property))
        .getOrElse(fallback)
        .trim();
  }
}
