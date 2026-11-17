package uz.khakimjanovich.platform.buildlogic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogContractTest {

  @TempDir Path build;

  @Test
  void aBuildWithoutTheCatalogIsToldToImportIt() throws IOException {
    write("settings.gradle", "dependencyResolutionManagement { repositories { mavenCentral() } }");
    write("build.gradle", "plugins { id 'platform.java' }");

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("No version catalog `libs`"), output);
  }

  @Test
  void aCatalogMissingAnAliasNamesIt() throws IOException {
    var catalog =
        Files.readString(Path.of(System.getProperty("catalog")))
            .replaceAll("(?m)^lombok = .*$", "")
            .replaceAll("(?m)^java = .*$", "");
    write("libs.versions.toml", catalog);
    write(
        "settings.gradle",
        """
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('libs.versions.toml')) } }
        }
        """);
    write("build.gradle", "plugins { id 'platform.java' }");

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("The catalog `libs` lacks lombok, versions.java"), output);
  }

  @Test
  void aLibraryWithoutItsOwnVersionIsToldWhichToAdd() throws IOException {
    write(
        "settings.gradle",
        """
        rootProject.name = 'newcomer'
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
    write("build.gradle", "plugins { id 'platform.library' }");

    var output = runner().buildAndFail().getOutput();

    assertTrue(output.contains("add platform-newcomer to the catalog's [versions]"), output);
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(build.toFile())
        .withPluginClasspath()
        .withArguments("help");
  }

  private void write(String path, String text) throws IOException {
    var file = build.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
