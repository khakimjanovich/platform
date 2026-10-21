package uz.heeypay.ecom.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Every library's own settings come with its jar: no app imports them. Each jar lists its YAML
 * files in {@code META-INF/spring/uz.heeypay.ecom.config.LibraryDefaults.imports}, one per line,
 * read the way Boot reads {@code AutoConfiguration.imports}. They are added after the app's own
 * configuration, so any app file, profile, environment variable or argument overrides them.
 */
public class LibraryDefaults implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    var sources = environment.getPropertySources();
    var files = ImportCandidates.load(LibraryDefaults.class, application.getClassLoader());
    try {
      for (var file : files) {
        if (sources.contains(file)) continue;
        new YamlPropertySourceLoader()
            .load(file, new ClassPathResource(file, application.getClassLoader()))
            .forEach(sources::addLast);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** After the app's configuration files are loaded, so these come last. */
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }
}
