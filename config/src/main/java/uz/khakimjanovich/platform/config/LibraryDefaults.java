package uz.khakimjanovich.platform.config;

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

  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }
}
