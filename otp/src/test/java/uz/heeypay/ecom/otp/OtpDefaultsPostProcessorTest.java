package uz.heeypay.ecom.otp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/** The library's own settings are there without an import, and the app's own values win. */
class OtpDefaultsPostProcessorTest {

  private static final String PROPERTY = "exception.starter.error.OTP_SEND_LIMIT.http-code";

  @Configuration(proxyBeanMethods = false)
  static class App {}

  @Test
  void theLibrarySettingsLoadWithoutAnImport() {
    try (var context = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run()) {
      assertThat(context.getEnvironment().getProperty(PROPERTY)).isEqualTo("429");
    }
  }

  @Test
  void anAppConfigFileOverridesThem() {
    try (var context =
        new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .run("--spring.config.location=classpath:/override.yaml")) {
      assertThat(context.getEnvironment().getProperty(PROPERTY)).isEqualTo("503");
    }
  }
}
