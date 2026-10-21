package uz.heeypay.ecom.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The contract plugin on a real build: a spec in, server and client interfaces out. */
class ContractPluginTest {

  @TempDir Path build;

  @Test
  void aContractGetsServerAndClientInterfacesWithDistinctNamesAndNoJackson2() throws IOException {
    write(
        "settings.gradle",
        """
        pluginManagement { includeBuild '%s' }
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        include 'demo-api'
        """
            .formatted(Path.of("").toAbsolutePath(), System.getProperty("catalog")));
    write(
        "demo-api/build.gradle",
        """
        plugins { id 'ecom.contract' }
        contract { client = true }
        """);
    write(
        "demo-api/src/main/openapi/demo-v1.yaml",
        """
        openapi: 3.0.3
        info: { title: Demo, version: v1 }
        paths:
          /demo/{id}:
            get:
              tags: [Demo]
              operationId: getDemo
              parameters:
                - { name: id, in: path, required: true, schema: { type: string } }
              responses:
                '200':
                  description: The demo.
                  content:
                    application/json:
                      schema: { $ref: '#/components/schemas/Demo' }
                '404': { $ref: '../../../../common/problem-v1.yaml#/components/responses/Problem' }
        components:
          schemas:
            Demo:
              type: object
              properties:
                tags: { type: array, uniqueItems: true, items: { type: string } }
        """);

    Files.createDirectories(build.resolve("common"));
    Files.copy(Path.of(System.getProperty("sharedProblem")), build.resolve("common/problem-v1.yaml"));

    var result = gradle("compileJava", "processResources", ":demo-api:dependencies", "--configuration", "runtimeClasspath");

    var generated = build.resolve("demo-api/build/generated");
    assertTrue(Files.exists(generated.resolve("openapi/src/main/java/uz/heeypay/ecom/demo/api/DemoApi.java")));
    assertTrue(
        Files.exists(
            generated.resolve(
                "openapi-client/src/main/java/uz/heeypay/ecom/demo/api/client/DemoClient.java")));
    assertFalse(
        Files.exists(
            generated.resolve("openapi-client/src/main/java/uz/heeypay/ecom/demo/api/client/DemoApi.java")),
        "the client interface never shares the server interface's name");
    assertFalse(
        result.contains("com.fasterxml.jackson.core:jackson-databind"),
        "what the jar brings has no Jackson 2 databind");
    assertTrue(result.contains("com.fasterxml.jackson.core:jackson-annotations"));

    try (var files = Files.walk(generated)) {
      assertTrue(
          files.noneMatch(file -> file.getFileName().toString().startsWith("Problem")),
          "the shared Problem is Spring's ProblemDetail, never a generated class");
    }
    try (var files = Files.walk(generated)) {
      assertTrue(
          files.noneMatch(
              file ->
                  file.toString().contains("org/openapitools")
                      || file.getFileName().toString().equals("ApiUtil.java")),
          "no supporting files: every contract jar would carry the same classes");
    }
    var served = build.resolve("demo-api/build/resources/main/static/openapi");
    assertTrue(Files.exists(served.resolve("problem-v1.yaml")), "Swagger UI finds the shared file");
    assertTrue(
        Files.readString(served.resolve("demo-v1.yaml"))
            .contains("$ref: 'problem-v1.yaml#/components/responses/Problem'"));
  }

  private String gradle(String... arguments) {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withArguments(arguments)
        .forwardOutput()
        .build()
        .getOutput();
  }

  private void write(String path, String content) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
