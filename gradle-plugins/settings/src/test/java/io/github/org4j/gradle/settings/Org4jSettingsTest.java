package io.github.org4j.gradle.settings;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Org4jSettingsTest {

  // Each catalog says where it came from, so a test sees which one the source picked.
  @TempDir static Path registry;
  @TempDir static Path mavenLocal;
  @TempDir static Path publisher;
  @TempDir static Path library;
  @TempDir Path consumer;

  @BeforeAll
  static void catalogsInBothRepositories() throws IOException {
    write(publisher, "settings.gradle", "rootProject.name = 'version-catalog'");
    write(
        publisher,
        "build.gradle",
        """
        plugins {
            id 'version-catalog'
            id 'maven-publish'
        }
        group = 'io.github.org4j'
        version = providers.gradleProperty('v').get()
        catalog { versionCatalog { version('origin', providers.gradleProperty('origin').get()) } }
        publishing {
            publications { catalog(MavenPublication) { from components.versionCatalog } }
            repositories { maven { url = providers.gradleProperty('repo').get() } }
        }
        """);
    write(
        library,
        "build.gradle",
        """
        plugins {
            id 'java-library'
            id 'maven-publish'
        }
        group = 'io.github.org4j'
        version = '1.0.0'
        publishing {
            publications { lib(MavenPublication) { from components.java } }
            repositories { maven { url = providers.gradleProperty('repo').get() } }
        }
        """);
    write(library, "settings.gradle", "rootProject.name = 'probe4j'");
    runner(library, "publish", "-Prepo=" + registry.toUri()).build();
    publish("9.9.9", "registry-release", registry);
    publish("9.x-SNAPSHOT", "registry-snapshot", registry);
    publish("9.x-SNAPSHOT", "maven-local", mavenLocal);
  }

  @Test
  void releaseTakesTheReleaseFromTheRegistry() throws IOException {
    var output = consumer("release").build().getOutput();

    assertTrue(output.contains("origin=registry-release"), output);
  }

  @Test
  void aSnapshotVersionIsRefused() throws IOException {
    var output = consumer("release", "-Porg4jVersion=9.x-SNAPSHOT").buildAndFail().getOutput();

    assertTrue(output.contains("write a release, X.Y.Z"), output);
  }

  @Test
  void localTakesTheLineFromMavenLocal() throws IOException {
    var output = consumer("local").build().getOutput();

    assertTrue(output.contains("origin=maven-local"), output);
  }

  @Test
  void aLibraryInItsOwnRepositoryComesFromTheRegistry() throws IOException {
    write(consumer, "settings.gradle", "plugins { id 'io.github.org4j.settings' }");
    write(
        consumer,
        "build.gradle",
        """
        configurations { probe }
        dependencies { probe 'io.github.org4j:probe4j:1.0.0' }
        tasks.register('probe') {
            def files = configurations.probe
            doLast { println "resolved=${files.singleFile.name}" }
        }
        """);

    var output =
        runner(consumer, "probe", "-Porg4jSource=release", "-Porg4jVersion=9.9.9")
            .build()
            .getOutput();

    assertTrue(output.contains("resolved=probe4j-1.0.0.jar"), output);
  }

  @Test
  void snapshotIsNoSourceAnyMore() throws IOException {
    var output = consumer("snapshot").buildAndFail().getOutput();

    assertTrue(output.contains("set it to one of [local, release]"), output);
  }

  @Test
  void anUnknownSourceNamesTheChoices() throws IOException {
    var output = consumer("nexus").buildAndFail().getOutput();

    assertTrue(output.contains("set it to one of [local, release]"), output);
  }

  @Test
  void aBuildWithoutAVersionIsToldToSetIt() throws IOException {
    write(consumer, "settings.gradle", "plugins { id 'io.github.org4j.settings' }");
    write(consumer, "build.gradle", "");

    var output = runner(consumer, "help", "-Porg4jSource=release").buildAndFail().getOutput();

    assertTrue(output.contains("org4jVersion is not set"), output);
  }

  private GradleRunner consumer(String source, String... extra) throws IOException {
    write(consumer, "settings.gradle", "plugins { id 'io.github.org4j.settings' }");
    write(
        consumer,
        "build.gradle",
        """
        tasks.register('origin') {
            def origin = libs.versions.origin.get()
            doLast { println "origin=$origin" }
        }
        """);
    var arguments = new ArrayList<>(List.of("origin", "-Porg4jSource=" + source));
    arguments.add("-Porg4jVersion=9.9.9");
    arguments.addAll(List.of(extra));
    return runner(consumer, arguments.toArray(String[]::new));
  }

  private static void publish(String version, String origin, Path repository) {
    runner(
            publisher,
            "publish",
            "-Pv=" + version,
            "-Porigin=" + origin,
            "-Prepo=" + repository.toUri())
        .build();
  }

  private static GradleRunner runner(Path project, String... arguments) {
    var all = new ArrayList<>(List.of(arguments));
    all.add("-Porg4jRegistry=" + registry.toUri());
    all.add("-Dmaven.repo.local=" + mavenLocal); // mavenLocal() reads this instead of ~/.m2
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(all);
  }

  private static void write(Path dir, String path, String text) throws IOException {
    var file = dir.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
