package uz.khakimjanovich.platform.buildlogic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformFirstTest {

  @TempDir Path build;

  @BeforeEach
  void aBuildOnThePlatform() throws IOException {
    write(
        "settings.gradle",
        """
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
        plugins { id 'platform.java' }
        dependencies {
            implementation 'org.springframework.boot:spring-boot-starter-data-redis'
            runtimeOnly 'io.lettuce:lettuce-core'
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains(
            "org.springframework.boot:spring-boot-starter-data-redis: use libs.platform.valkey"),
        output);
    assertTrue(output.contains("io.lettuce:lettuce-core: use libs.platform.valkey"), output);
  }

  @Test
  void aServiceOnThePlatformsLibrariesPasses() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-web' }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("but the platform provides it"), output);
  }

  @Test
  void aPlatformLibraryDeclaresWhatItWraps() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'platform.library' }
        dependencies { api 'org.springframework.boot:spring-boot-starter-data-redis' }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("but the platform provides it"), output);
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("verifyPlatformFirst");
  }

  private void write(String path, String text) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
