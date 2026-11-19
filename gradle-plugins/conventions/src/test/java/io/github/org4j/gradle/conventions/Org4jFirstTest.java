package io.github.org4j.gradle.conventions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Org4jFirstTest {

  @TempDir Path build;

  @BeforeEach
  void aBuildOnOrg4j() throws IOException {
    write("gradle.properties", "version=0.0.0");
    write(
        "settings.gradle",
        """
        rootProject.name = 'valkey'
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
  }

  @Test
  void aServiceThatDeclaresRedisItselfFailsTheBuild() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'io.github.org4j.java' }
        dependencies {
            implementation 'org.springframework.boot:spring-boot-starter-data-redis'
            runtimeOnly 'io.lettuce:lettuce-core'
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains(
            "org.springframework.boot:spring-boot-starter-data-redis: use libs.valkey"),
        output);
    assertTrue(output.contains("io.lettuce:lettuce-core: use libs.valkey"), output);
  }

  @Test
  void aServiceOnOrg4jsLibrariesPasses() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'io.github.org4j.java' }
        dependencies { implementation 'org.springframework:spring-web' }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("but org4j provides it"), output);
  }

  @Test
  void anOrg4jLibraryDeclaresWhatItWraps() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'io.github.org4j.library' }
        dependencies { api 'org.springframework.boot:spring-boot-starter-data-redis' }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("but org4j provides it"), output);
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("verifyOrg4jFirst");
  }

  private void write(String path, String text) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
