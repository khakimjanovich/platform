package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/** The library's own settings are there without an import, and the app's own values win. */
class FileStoreDefaultsPostProcessorTest {

  private static final String PROPERTY = "exception.starter.error.FILE_STORE_FILE_EMPTY.http-code";

  @Configuration(proxyBeanMethods = false)
  static class App {}

  @Test
  void theLibrarySettingsLoadWithoutAnImport() {
    try (var context = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run()) {
      assertThat(context.getEnvironment().getProperty(PROPERTY)).isEqualTo("400");
    }
  }

  @Test
  void anAppConfigFileOverridesThem() {
    try (var context =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .run("--spring.config.location=classpath:/override.yaml")) {
      assertThat(context.getEnvironment().getProperty(PROPERTY)).isEqualTo("422");
    }
  }
}
