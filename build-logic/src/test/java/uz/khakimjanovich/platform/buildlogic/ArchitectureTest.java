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
  void aHandBuiltMapBodyFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import java.util.Map;

        public class DemoService {
          public Map<String, Object> body(String jobId) {
            return Map.of("job_id", jobId);
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("never a Map<String, Object>"), output);
    assertTrue(output.contains("DemoService.body(java.lang.String)"), output);
  }

  @Test
  void aServicePackageOutOfPlaceFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/JobRequest.java",
        "package demo.service;\n\npublic record JobRequest(String jobId) {}\n");
    write(
        "src/main/java/demo/config/DemoSettings.java",
        """
        package demo.config;

        @org.springframework.boot.context.properties.ConfigurationProperties("demo")
        public record DemoSettings(String host) {}
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework.boot:spring-boot' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("what goes over the wire is a DTO in model"), output);
    assertTrue(output.contains("demo.service.JobRequest"), output);
    assertTrue(output.contains("a service's settings live in property"), output);
    assertTrue(output.contains("demo.config.DemoSettings"), output);
  }

  @Test
  void aServiceThatMapsJsonFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import tools.jackson.databind.JsonNode;

        public class DemoService {
          public String jobId(JsonNode answer) {
            return answer.path("job_id").asString();
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'tools.jackson.core:jackson-databind' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a mapper in mapper maps a response"), output);
    assertTrue(output.contains("demo.service.DemoService"), output);
  }

  @Test
  void aServiceThatBuildsItsOwnRequestFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import org.springframework.web.client.RestClient;

        public class DemoService {
          private final RestClient http;

          public DemoService(RestClient http) {
            this.http = http;
          }

          public void ping() {
            http.get().uri("/api/v1/status").retrieve().toBodilessEntity();
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-web' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("an outgoing call is an @HttpExchange interface in client"), output);
    assertTrue(output.contains("demo.service.DemoService"), output);
  }

  @Test
  void aFormBuiltAsAMapFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/utils/Forms.java",
        """
        package demo.utils;

        import org.springframework.util.LinkedMultiValueMap;

        public final class Forms {
          private Forms() {}

          public static LinkedMultiValueMap<String, String> token(String clientId) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("client_id", clientId);
            return form;
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-core' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("never a map of name, value pairs"), output);
    assertTrue(output.contains("demo.utils.Forms"), output);
  }

  @Test
  void aBodyBuiltFromNameValuePairsFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/utils/Bodies.java",
        """
        package demo.utils;

        import java.util.ArrayList;
        import java.util.List;

        public final class Bodies {
          private Bodies() {}

          public static List<String> pairs(String... pairs) {
            return new ArrayList<>(List.of(pairs));
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("never built from name, value pairs"), output);
    assertTrue(output.contains("Bodies.pairs([Ljava.lang.String;)"), output);
  }

  @Test
  void aJsonTreeBuiltOutsideAMapperFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/utils/Trees.java",
        """
        package demo.utils;

        import tools.jackson.databind.node.JsonNodeFactory;
        import tools.jackson.databind.node.ObjectNode;

        public final class Trees {
          private Trees() {}

          public static ObjectNode body(String jobId) {
            return JsonNodeFactory.instance.objectNode().put("job_id", jobId);
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'tools.jackson.core:jackson-databind' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("only a mapper reshapes a JSON tree"), output);
    assertTrue(output.contains("demo.utils.Trees"), output);
  }

  @Test
  void aLibraryKeepsItsFlatPackage() throws IOException {
    write(
        "settings.gradle",
        """
        rootProject.name = 'demo-lib'
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
    write("build.gradle", "plugins { id 'platform.library' }\n");
    write(
        "src/main/java/demo/service/JobRequest.java",
        "package demo.service;\n\npublic record JobRequest(String jobId) {}\n");

    var output = runner().build().getOutput();

    assertFalse(output.contains("The architecture is broken"), output);
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
