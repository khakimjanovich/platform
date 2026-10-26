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

/** platform.java's verifyNullMarked on a real build: a main package without @NullMarked fails. */
class NullMarkedTest {

  @TempDir Path build;

  @BeforeEach
  void aBuildOnPlatformJava() throws IOException {
    write(
        "settings.gradle",
        """
        dependencyResolutionManagement {
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
    write("build.gradle", "plugins { id 'platform.java' }\n");
    write(
        "src/main/java/demo/package-info.java",
        """
        @NullMarked
        package demo;

        import org.jspecify.annotations.NullMarked;
        """);
    write("src/main/java/demo/Payment.java", "package demo;\n\nclass Payment {}\n");
  }

  @Test
  void anUnmarkedPackageFailsTheBuild() throws IOException {
    write("src/main/java/demo/card/Card.java", "package demo.card;\n\nclass Card {}\n");
    write("src/main/java/demo/bin/package-info.java", "package demo.bin;\n");
    write("src/main/java/demo/bin/Bin.java", "package demo.bin;\n\nclass Bin {}\n");

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("src/main/java/demo/bin"), output);
    assertTrue(output.contains("src/main/java/demo/card"), output);
    assertFalse(output.contains("src/main/java/demo\n"), output);
  }

  @Test
  void everyPackageMarkedPasses() {
    runner().build();
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("verifyNullMarked");
  }

  private void write(String path, String text) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
