package uz.heeypay.ecom.error;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * The error definitions every service shares come with the library: no app imports them. They are
 * added after the app's own configuration, so any app property overrides them.
 */
public class ErrorDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final List<String> FILES = List.of("properties/error-exception.yaml");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    var sources = environment.getPropertySources();
    try {
      for (var file : FILES) {
        if (sources.contains(file)) continue;
        new YamlPropertySourceLoader()
            .load(file, new ClassPathResource(file))
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
