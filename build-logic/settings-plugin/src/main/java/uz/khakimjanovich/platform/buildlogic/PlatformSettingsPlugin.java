package uz.khakimjanovich.platform.buildlogic;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.ProviderFactory;

/**
 * Where a build on the platform takes it from: {@code platformSource} is {@code local} (what
 * {@code ./gradlew publishLocal} put in ~/.m2), {@code snapshot} (develop's snapshots in the
 * registry) or {@code release} (main's releases). {@code platformVersion} is the hub version without
 * a suffix; local and snapshot add {@code -SNAPSHOT}. The plugin sets the platform's repositories,
 * imports its catalog as {@code libs}, and in snapshot mode makes every build take the newest
 * snapshot of the catalog, the convention plugins and the libraries.
 */
public final class PlatformSettingsPlugin implements Plugin<Settings> {

  static final String GROUP = "uz.khakimjanovich.platform";
  static final String PLUGIN_GROUPS = "platform\\..*"; // the convention plugins' markers
  static final String REGISTRY = "https://maven.pkg.github.com/khakimjanovich/platform";

  enum Source {
    LOCAL,
    SNAPSHOT,
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
    // Gradle caches a -SNAPSHOT it resolves in settings for 24 hours: pin the newest build instead.
    var pin = source == Source.SNAPSHOT && registry.http() && !settings.getStartParameter().isOffline();

    repositories(settings.getPluginManagement().getRepositories(), source, registry);
    var resolution = settings.getDependencyResolutionManagement();
    resolution.getRepositoriesMode().set(RepositoriesMode.PREFER_SETTINGS);
    repositories(resolution.getRepositories(), source, registry);
    resolution.getRepositories().mavenCentral();

    var catalog = pin ? latest(providers, registry, "versioning", version) : version;
    resolution.getVersionCatalogs().create("libs", libs -> libs.from(GROUP + ":versioning:" + catalog));

    if (pin) {
      settings
          .getPluginManagement()
          .getResolutionStrategy()
          .eachPlugin(
              plugin -> {
                var requested = plugin.getRequested().getVersion();
                if (plugin.getRequested().getId().getId().startsWith("platform.")
                    && requested != null
                    && requested.endsWith("-SNAPSHOT")) {
                  plugin.useModule(
                      GROUP + ":build-logic:" + latest(providers, registry, "build-logic", requested));
                }
              });
    }
    if (source != Source.RELEASE) {
      // The libraries: a snapshot is the newest build of its version, never a cached one.
      settings
          .getGradle()
          .getLifecycle()
          .beforeProject(
              project ->
                  project
                      .getConfigurations()
                      .configureEach(
                          configuration ->
                              configuration
                                  .getResolutionStrategy()
                                  .cacheChangingModulesFor(0, TimeUnit.SECONDS)));
    }
  }

  private static String latest(
      ProviderFactory providers, Registry registry, String artifact, String snapshot) {
    return providers
        .of(
            LatestSnapshot.class,
            spec -> {
              var parameters = spec.getParameters();
              parameters.getRegistry().set(registry.url().toString());
              parameters.getArtifact().set(artifact);
              parameters.getVersion().set(snapshot);
              parameters.getUser().set(registry.user());
              parameters.getToken().set(registry.token());
            })
        .getOrElse(snapshot);
  }

  // The platform's artifacts come only from these repositories (exclusiveContent): a mavenLocal()
  // that an init script or the build adds elsewhere can't hand a stale snapshot to a snapshot build.
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
              // local: the snapshots are this machine's; what it doesn't have is a release.
              if (source != Source.SNAPSHOT) {
                remote.mavenContent(content -> content.releasesOnly());
              }
            }));
    repositories.exclusiveContent(
        exclusive ->
            exclusive
                .forRepositories(platform.toArray(ArtifactRepository[]::new))
                .filter(
                    content -> {
                      content.includeGroup(GROUP);
                      content.includeGroupByRegex(PLUGIN_GROUPS);
                    }));
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

  private static String version(ProviderFactory providers, Source source) {
    var bare = read(providers, "platformVersion", "PLATFORM_VERSION", "");
    if (bare.isEmpty()) {
      throw new GradleException(
          "platformVersion is not set: the platform release this build uses, e.g. 2.8.0");
    }
    if (bare.endsWith("-SNAPSHOT")) {
      throw new GradleException(
          "platformVersion is "
              + bare
              + ": write it without -SNAPSHOT; platformSource=local or snapshot adds it");
    }
    return source == Source.RELEASE ? bare : bare + "-SNAPSHOT";
  }

  private static String read(
      ProviderFactory providers, String property, String variable, String fallback) {
    return providers
        .gradleProperty(property)
        .orElse(providers.environmentVariable(variable))
        .getOrElse(fallback)
        .trim();
  }
}
