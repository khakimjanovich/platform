package io.github.org4j.gradle.settings;

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
 * Where a build on org4j takes it from. {@code org4jVersion} is a hub release (1.0.0).
 * {@code org4jSource} is {@code release} (that release from the registry, releases only) or
 * {@code local} (its line, 1.x-SNAPSHOT, from ~/.m2 after {@code ./gradlew publishToMavenLocal} in
 * the hub and the libraries). The plugin sets org4j's repositories and imports its catalog as
 * {@code libs}.
 */
public final class Org4jSettingsPlugin implements Plugin<Settings> {

  static final String GROUP = "io.github.org4j";
  // The hub's artifacts, the libraries (io.github.org4j:config) and the plugin markers
  // (io.github.org4j.java).
  static final String GROUPS = "io\\.github\\.org4j(\\..+)?";
  static final String REGISTRY = "https://maven.pkg.github.com/org4j/*";
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
            URI.create(read(providers, "org4jRegistry", "ORG4J_REGISTRY", REGISTRY)),
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

  // org4j's artifacts come only from these repositories (exclusiveContent): a mavenLocal()
  // that an init script adds can't stand in for a release, and the registry hands out releases only.
  private static void repositories(
      RepositoryHandler repositories, Source source, Registry registry) {
    var org4j = new ArrayList<ArtifactRepository>();
    if (source == Source.LOCAL) {
      org4j.add(
          repositories.mavenLocal(
              local -> {
                local.setName("org4jLocal");
                local.mavenContent(content -> content.snapshotsOnly());
              }));
    }
    org4j.add(
        repositories.maven(
            remote -> {
              remote.setName("org4jRegistry");
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
                .forRepositories(org4j.toArray(ArtifactRepository[]::new))
                .filter(content -> content.includeGroupByRegex(GROUPS)));
  }

  private static Source source(ProviderFactory providers) {
    var value = read(providers, "org4jSource", "ORG4J_SOURCE", "");
    try {
      return Source.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new GradleException(
          "org4jSource is '"
              + value
              + "': set it to one of "
              + Arrays.toString(Source.values()).toLowerCase(Locale.ROOT)
              + " (gradle.properties, -Porg4jSource or ORG4J_SOURCE)",
          e);
    }
  }

  // release: the version as written; local: its line, 1.0.0 -> 1.x-SNAPSHOT.
  private static String version(ProviderFactory providers, Source source) {
    var release = read(providers, "org4jVersion", "ORG4J_VERSION", "");
    if (release.isEmpty()) {
      throw new GradleException(
          "org4jVersion is not set: org4j release this build uses, e.g. 1.0.0");
    }
    var matcher = RELEASE.matcher(release);
    if (!matcher.matches()) {
      throw new GradleException(
          "org4jVersion is " + release + ": write a release, X.Y.Z (e.g. 1.0.0)");
    }
    return source == Source.RELEASE ? release : matcher.group(1) + ".x-SNAPSHOT";
  }

  // The variable wins: CI sets ORG4J_SOURCE=release on a tag over what a repository keeps in
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
