package uz.heeypay.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/** A library's defaults load without an import, below every way an app configures itself. */
class LibraryDefaultsTest {

  @Configuration(proxyBeanMethods = false)
  static class App {}

  private static SpringApplicationBuilder app() {
    return new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE);
  }

  @Test
  void theDefaultsLoadWithoutAnImport() {
    try (var context = app().run()) {
      assertThat(context.getEnvironment().getProperty("demo.value")).isEqualTo("library");
    }
  }

  @Test
  void anAppConfigFileOverridesThem() {
    try (var context = app().run("--spring.config.location=classpath:/override.yaml")) {
      assertThat(context.getEnvironment().getProperty("demo.value")).isEqualTo("file");
      assertThat(context.getEnvironment().getProperty("demo.other")).isEqualTo("library");
    }
  }

  @Test
  void aCommandLineArgumentOverridesThem() {
    try (var context = app().run("--demo.other=arg")) {
      assertThat(context.getEnvironment().getProperty("demo.other")).isEqualTo("arg");
    }
  }

  @Test
  void aParentAndChildAddThemOnce() {
    try (var context = app().child(App.class).web(WebApplicationType.NONE).run()) {
      var sources = context.getEnvironment().getPropertySources().stream();
      assertThat(sources.filter(source -> source.getName().contains("test-defaults.yaml")))
          .hasSize(1);
    }
  }
}
