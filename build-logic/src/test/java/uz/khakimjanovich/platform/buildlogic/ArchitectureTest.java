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

/** platform.java's verifyArchitecture on a real build: a broken layer or constant fails `check`. */
class ArchitectureTest {

  @TempDir Path build;

  @BeforeEach
  void aBuildOnPlatformJava() throws IOException {
    write(
        "settings.gradle",
        """
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
    write("build.gradle", "plugins { id 'platform.java' }\n");
    for (var pkg : new String[] {"controller", "service", "config", "utils"}) {
      write(
          "src/main/java/demo/" + pkg + "/package-info.java",
          "@org.jspecify.annotations.NullMarked\npackage demo." + pkg + ";\n");
    }
    write(
        "src/main/java/demo/config/DemoProperties.java",
        "package demo.config;\n\npublic record DemoProperties(String host) {}\n");
    write(
        "src/main/java/demo/utils/ConstantUtils.java",
        """
        package demo.utils;

        public final class ConstantUtils {
          public static final String HEALTH_PATH = "/actuator/health";

          private ConstantUtils() {}
        }
        """);
  }

  @Test
  void aControllerThatReadsConfigurationFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/controller/DemoController.java",
        """
        package demo.controller;

        import demo.config.DemoProperties;

        public class DemoController {
          private final DemoProperties properties;

          public DemoController(DemoProperties properties) {
            this.properties = properties;
          }

          public String host() {
            return properties.host();
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a controller calls services"), output);
    assertTrue(output.contains("demo.controller.DemoController"), output);
  }

  @Test
  void aConstantOutsideAConstantClassFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import java.time.Duration;

        public class DemoService {
          private static final Duration TIMEOUT = Duration.ofSeconds(3);

          public Duration timeout() {
            return TIMEOUT;
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("constants are kept apart"), output);
    assertTrue(output.contains("DemoService.TIMEOUT"), output);
  }

  @Test
  void layersAndConstantsKeptPass() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import static demo.utils.ConstantUtils.HEALTH_PATH;

        import demo.config.DemoProperties;

        public class DemoService {
          private final DemoProperties properties;

          public DemoService(DemoProperties properties) {
            this.properties = properties;
          }

          public String health() {
            return properties.host() + HEALTH_PATH;
          }
        }
        """);
    write(
        "src/main/java/demo/controller/DemoController.java",
        """
        package demo.controller;

        import demo.service.DemoService;

        public class DemoController {
          private final DemoService service;

          public DemoController(DemoService service) {
            this.service = service;
          }

          public String health() {
            return service.health();
          }
        }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("The architecture is broken"), output);
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("verifyArchitecture");
  }

  private void write(String path, String text) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
