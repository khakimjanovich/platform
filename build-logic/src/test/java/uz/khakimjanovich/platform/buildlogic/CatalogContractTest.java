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

  @Test
  void aLibraryInItsOwnRepositoryKeepsItsOwnVersion() throws IOException {
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
    write("gradle.properties", "version=1.2.3-SNAPSHOT");
    write(
        "build.gradle",
        """
        plugins { id 'platform.library' }
        tasks.register('printVersion') {
            def v = version
            doLast { println "version=$v" }
        }
        """);

    var output = runner().withArguments("printVersion").build().getOutput();

    assertTrue(output.contains("version=1.2.3-SNAPSHOT"), output);
  }

  @Test
  void aMovedLibraryOwnsItsCoordinatesAndAnnouncesItsFormerOnes() throws IOException {
    write(
        "settings.gradle",
        """
        rootProject.name = 'config4j'
        dependencyResolutionManagement {
            repositories { mavenCentral() }
            versionCatalogs { create('libs') { from(files('%s')) } }
        }
        """
            .formatted(System.getProperty("catalog")));
    write(
        "gradle.properties",
        """
        group=uz.khakimjanovich
        version=2.8.0-SNAPSHOT
        formerCoordinates=uz.khakimjanovich.platform:config
        """);
    write("build.gradle", "plugins { id 'platform.library' }");

    var output =
        runner()
            .withArguments("outgoingVariants", "--variant", "runtimeElements")
            .build()
            .getOutput();

    assertTrue(output.contains("uz.khakimjanovich:config4j:2.8.0-SNAPSHOT"), output);
    assertTrue(output.contains("uz.khakimjanovich.platform:config:2.8.0-SNAPSHOT"), output);
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
