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
  void settingsReadFieldByFieldFromJsonFailTheBuild() throws IOException {
    write(
        "src/main/java/demo/property/package-info.java",
        "@org.jspecify.annotations.NullMarked\npackage demo.property;\n");
    write(
        "src/main/java/demo/property/Callers.java",
        """
        package demo.property;

        import tools.jackson.databind.JsonNode;

        public class Callers {
          public String origin(JsonNode record) {
            return record.path("origin").asString();
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

    assertTrue(output.contains("JSON is read into a DTO in model"), output);
    assertTrue(output.contains("demo.property.Callers"), output);
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
  void aServiceThatHandlesAClientsFailurePasses() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import org.springframework.web.client.RestClientException;

        public class DemoService {
          public boolean answered(Runnable call) {
            try {
              call.run();
              return true;
            } catch (RestClientException e) {
              return e.getMessage() == null;
            }
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-web' }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("The architecture is broken"), output);
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
  void aModuleThatBuildsItsOwnPoolFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/config/DemoPersistenceConfig.java",
        """
        package demo.config;

        import com.zaxxer.hikari.HikariDataSource;

        public class DemoPersistenceConfig {
          public HikariDataSource pool() {
            return new HikariDataSource();
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'com.zaxxer:HikariCP' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("@EnableModulePersistence builds them from the platform"), output);
    assertTrue(output.contains("demo.config.DemoPersistenceConfig"), output);
  }

  @Test
  void aServiceThatThrowsAGenericExceptionFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        public class DemoService {
          public String hash(String text) {
            if (text.isEmpty()) {
              throw new IllegalStateException("no text");
            }
            return text;
          }
        }
        """);
    write(
        "src/main/java/demo/service/DemoFailure.java",
        """
        package demo.service;

        public class DemoFailure extends RuntimeException {
          private static final long serialVersionUID = 1L;

          public DemoFailure(String message) {
            super(message);
          }

          public static DemoFailure of(String message) {
            return new DemoFailure(message);
          }
        }
        """);

    write(
        "src/main/java/demo/service/DemoTexts.java",
        """
        package demo.service;

        public final class DemoTexts {
          private DemoTexts() {
            throw new UnsupportedOperationException("a private constructor's guard passes");
          }

          public enum Kind { ONE, TWO }

          public static String name(Kind kind) {
            return switch (kind) {
              case ONE -> "one";
              case TWO -> "two";
            };
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a service throws GeneralException"), output);
    assertTrue(output.contains("java.lang.IllegalStateException"), output);
    assertTrue(output.contains("demo.service.DemoFailure.<init>"), output);
    assertFalse(output.contains("UnsupportedOperationException"), output);
    assertFalse(output.contains("MatchException"), output);
  }

  @Test
  void aServiceMayThrowItsOwnGeneralException() throws IOException {
    // A stand-in for the error library's class, with the same name.
    write(
        "src/main/java/uz/khakimjanovich/platform/error/package-info.java",
        "@org.jspecify.annotations.NullMarked\npackage uz.khakimjanovich.platform.error;\n");
    write(
        "src/main/java/uz/khakimjanovich/platform/error/GeneralException.java",
        """
        package uz.khakimjanovich.platform.error;

        public class GeneralException extends RuntimeException {
          private static final long serialVersionUID = 1L;

          public GeneralException(String code) {
            super(code);
          }
        }
        """);
    write(
        "src/main/java/demo/service/EmailTaken.java",
        """
        package demo.service;

        import uz.khakimjanovich.platform.error.GeneralException;

        public class EmailTaken extends GeneralException {
          private static final long serialVersionUID = 1L;

          public EmailTaken() {
            super("PARTNER_EMAIL_TAKEN");
          }
        }
        """);
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import uz.khakimjanovich.platform.error.GeneralException;

        public class DemoService {
          public void register(boolean taken, boolean down) {
            if (taken) {
              throw new EmailTaken();
            }
            if (down) {
              throw new GeneralException("SERVICE_UNAVAILABLE");
            }
          }
        }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("The architecture is broken"), output);
  }

  @Test
  void configurationMayStopStartupWithAnyException() throws IOException {
    write(
        "src/main/java/demo/config/DemoChecks.java",
        """
        package demo.config;

        public final class DemoChecks {
          private DemoChecks() {}

          public static void host(String host) {
            if (host.isBlank()) {
              throw new IllegalStateException("demo.host is not set");
            }
          }
        }
        """);

    var output = runner().build().getOutput();

    assertFalse(output.contains("The architecture is broken"), output);
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

  @Test
  void fieldInjectionFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import org.springframework.beans.factory.annotation.Autowired;
        import demo.config.DemoProperties;

        public class DemoService {
          @Autowired
          private DemoProperties properties;

          public String host() {
            return properties.host();
          }
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-beans' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains("dependencies must be injected via constructors, never fields"), output);
    assertTrue(output.contains("DemoService.properties"), output);
  }

  @Test
  void standardStreamsAccessFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        public class DemoService {
          public void log(String message) {
            System.out.println(message);
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains(
            "production code must use structured logging (SLF4J), never System.out, System.err, or"
                + " printStackTrace"),
        output);
    assertTrue(output.contains("DemoService.log(java.lang.String)"), output);
  }

  @Test
  void javaUtilLoggingFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import java.util.logging.Logger;

        public class DemoService {
          private static final Logger log = Logger.getLogger(DemoService.class.getName());

          public void info(String message) {
            log.info(message);
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains("production code must use SLF4J, never java.util.logging"), output);
    assertTrue(output.contains("DemoService"), output);
  }

  @Test
  void utilityClassWithoutPrivateConstructorFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/utils/NumberUtils.java",
        """
        package demo.utils;

        public final class NumberUtils {
          public NumberUtils() {}

          public static int add(int a, int b) {
            return a + b;
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains(
            "a utility class is final with a private constructor and only static members"),
        output);
    assertTrue(output.contains("demo.utils.NumberUtils"), output);
  }

  @Test
  void utilityClassWithNonStaticMethodFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/utils/StringUtils.java",
        """
        package demo.utils;

        public final class StringUtils {
          private StringUtils() {}

          public String trim(String text) {
            return text.strip();
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains(
            "a utility class is final with a private constructor and only static members"),
        output);
    assertTrue(output.contains("demo.utils.StringUtils"), output);
  }

  @Test
  void serviceAnnotatedOutsideServicePackageFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/config/DemoService.java",
        """
        package demo.config;

        import org.springframework.stereotype.Service;

        @Service
        public class DemoService {}
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-context' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a service lives in service"), output);
    assertTrue(output.contains("demo.config.DemoService"), output);
  }

  @Test
  void repositoryOutsideRepositoryPackageFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/OrderRepository.java",
        """
        package demo.service;

        import org.springframework.stereotype.Repository;

        @Repository
        public class OrderRepository {}
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-context' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a repository lives in repository"), output);
    assertTrue(output.contains("demo.service.OrderRepository"), output);
  }

  @Test
  void repositoryThatDependsOnServiceFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/repository/package-info.java",
        "@org.jspecify.annotations.NullMarked\npackage demo.repository;\n");
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        public class DemoService {
          public String compute() { return "ok"; }
        }
        """);
    write(
        "src/main/java/demo/repository/DemoRepository.java",
        """
        package demo.repository;

        import demo.service.DemoService;

        public class DemoRepository {
          private final DemoService service;

          public DemoRepository(DemoService service) {
            this.service = service;
          }

          public String find() {
            return service.compute();
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("a repository never depends on services or controllers"), output);
    assertTrue(output.contains("demo.repository.DemoRepository"), output);
  }

  @Test
  void cyclicDependenciesBetweenSlicesFailTheBuild() throws IOException {
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import demo.workflow.DemoWorkflow;

        public class DemoService {
          private final DemoWorkflow workflow;

          public DemoService(DemoWorkflow workflow) {
            this.workflow = workflow;
          }

          public void start() {
            workflow.run();
          }
        }
        """);
    write(
        "src/main/java/demo/workflow/package-info.java",
        "@org.jspecify.annotations.NullMarked\npackage demo.workflow;\n");
    write(
        "src/main/java/demo/workflow/DemoWorkflow.java",
        """
        package demo.workflow;

        import demo.service.DemoService;

        public class DemoWorkflow {
          public void run() {}

          public void callService(DemoService service) {
            service.start();
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("service layers must be free of cyclic dependencies"), output);
    assertTrue(output.contains("Cycle detected"), output);
  }

  @Test
  void controllerWithTransactionalFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/controller/DemoController.java",
        """
        package demo.controller;

        import org.springframework.transaction.annotation.Transactional;

        @Transactional
        public class DemoController {
          public void handle() {}
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-tx' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains("transactions belong in the service layer, never in controllers"), output);
    assertTrue(output.contains("demo.controller.DemoController"), output);
  }

  @Test
  void repositoryThatDependsOnWebClassesFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/repository/package-info.java",
        "@org.jspecify.annotations.NullMarked\npackage demo.repository;\n");
    write(
        "src/main/java/demo/repository/DemoRepository.java",
        """
        package demo.repository;

        import org.springframework.web.bind.annotation.RequestParam;

        public class DemoRepository {
          public void search(@RequestParam String term) {}
        }
        """);
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        dependencies { implementation 'org.springframework:spring-web' }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("repositories must not depend on web layer classes"), output);
    assertTrue(output.contains("demo.repository.DemoRepository"), output);
  }

  @Test
  void serviceThatDependsOnControllerFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/controller/DemoController.java",
        """
        package demo.controller;

        public class DemoController {
          public String status() { return "ok"; }
        }
        """);
    write(
        "src/main/java/demo/service/DemoService.java",
        """
        package demo.service;

        import demo.controller.DemoController;

        public class DemoService {
          private final DemoController controller;

          public DemoService(DemoController controller) {
            this.controller = controller;
          }

          public String check() {
            return controller.status();
          }
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(
        output.contains("services must never depend on the presentation layer"), output);
    assertTrue(output.contains("demo.service.DemoService"), output);
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
