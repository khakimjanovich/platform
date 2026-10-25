package uz.heeypay.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/** A setting nothing gives a value stops startup and is named; given one, it starts. */
class RequiredSettingsTest {

  @Configuration(proxyBeanMethods = false)
  static class App {}

  private static final String FILE = "--spring.config.location=classpath:/required.yaml";

  @Test
  void aMissingSettingStopsStartup() {
    assertThatThrownBy(
            () -> new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run(FILE))
        .hasStackTraceContaining("demo.required")
        .hasStackTraceContaining("DEMO_REQUIRED");
  }

  @Test
  void aGivenSettingStarts() {
    try (var context =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .run(FILE, "--DEMO_REQUIRED=set")) {
      assertThat(context.getEnvironment().getProperty("demo.required")).isEqualTo("set");
    }
  }
}
