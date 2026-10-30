package uz.khakimjanovich.platform.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** platform.java's verifyNoWorkarounds on a real build: what marks a workaround fails `check`. */
class NoWorkaroundsTest {

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
  }

  @Test
  void aTodoInMainCodeFailsTheBuild() throws IOException {
    write(
        "src/main/java/demo/Payment.java",
        """
        package demo;

        class Payment {
          // TODO(open question 12): refund rules
          void refund() {}

          // A temporary fix until the bank answers.
          void retry() {}

          /*
           * Sent twice. A workaround, see the bank's ticket.
           */
          void send() {}
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("src/main/java/demo/Payment.java:4: a workaround comment"), output);
    assertTrue(output.contains("src/main/java/demo/Payment.java:7: a workaround comment"), output);
    assertTrue(output.contains("src/main/java/demo/Payment.java:11: a workaround comment"), output);
  }

  @Test
  void aSuppressionWithoutAReasonAndADisabledTestFailTheBuild() throws IOException {
    write(
        "src/main/java/demo/Cards.java",
        """
        package demo;

        class Cards {
          @SuppressWarnings("unchecked")
          void list() {}
        }
        """);
    write(
        "src/test/java/demo/CardsTest.java",
        """
        package demo;

        class CardsTest {
          @org.junit.jupiter.api.Disabled
          void lists() {}
        }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("Cards.java:4: @SuppressWarnings without a // reason"), output);
    assertTrue(output.contains("CardsTest.java:4: a disabled test"), output);
  }

  @Test
  void aReasonedSuppressionAPonytailNoteAndPlainWordsPass() throws IOException {
    write(
        "src/main/java/demo/Otp.java",
        """
        package demo;

        class Otp {
          static final String TEMPORARY = "temporary"; // Keycloak's credential flag
          // The partners the card is bound to, by temporary token.

          // ponytail: one global limit for now; per-merchant limits when a merchant asks
          // The cast changes only the static type.
          @SuppressWarnings("unchecked")
          void issue() {}

          /** Uses {@code @SuppressWarnings} nowhere else. */
          void plainWords(int i) {
            String url = "https://example.com/api?note=TODO list app";
            String order = "Order #" + i + " workaround mode";
            i--;
            String later = "for now";
          }
        }
        """);

    var result = runner().build();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }

  @Test
  void aTurnedOffCheckInTheBuildFileFailsTheBuild() throws IOException {
    write(
        "build.gradle",
        """
        plugins { id 'platform.java' }
        tasks.withType(Test).configureEach { ignoreFailures = true }
        """);

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("build.gradle:2: a check turned off"), output);
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("verifyNoWorkarounds");
  }

  private void write(String path, String content) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
