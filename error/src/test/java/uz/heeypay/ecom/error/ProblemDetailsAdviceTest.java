package uz.heeypay.ecom.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every error a client can see is a problem detail with a stable code, and nothing internal leaks.
 */
class ProblemDetailsAdviceTest {

  record Signup(@Size(min = 2) String name, @Email String email) {}

  /** A service's own errors; EMAIL_TAKEN is defined below, NOT_DEFINED nowhere. */
  enum TestStatus implements StatusThrowable {
    EMAIL_TAKEN,
    NOT_DEFINED;

    @Override
    public String getType() {
      return name();
    }
  }

  @RestController
  static class Endpoints {
    @PostMapping("/signup")
    void signup(@Valid @RequestBody Signup body) {}

    @GetMapping("/taken")
    void taken() {
      throw new GeneralException(TestStatus.EMAIL_TAKEN, "aziz@example.uz");
    }

    @GetMapping("/refused")
    void refused() {
      throw new GeneralException(TestStatus.EMAIL_TAKEN, "aziz@example.uz")
          .subError("-240", "Insufficient funds!");
    }

    @GetMapping("/undefined")
    void undefined() {
      throw new GeneralException(TestStatus.NOT_DEFINED);
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("db password is hunter2");
    }
  }

  private final MockMvc mvc = mvc();

  @Test
  void invalidInputListsTheFailingFields() throws Exception {
    mvc.perform(
            post("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"email\":\"nope\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
            jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("name", "email")));
  }

  @Test
  void ownErrorsTakeStatusTitleAndMessageFromTheirDefinition() throws Exception {
    mvc.perform(get("/taken"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
        .andExpect(jsonPath("$.title").value("email-taken"))
        .andExpect(jsonPath("$.detail").value("aziz@example.uz is already registered."));
    mvc.perform(get("/taken").header("Accept-Language", "uz"))
        .andExpect(jsonPath("$.detail").value("aziz@example.uz allaqachon roʻyxatdan oʻtgan."));
  }

  @Test
  void aThirdPartysOwnCodesTravelAsSubErrors() throws Exception {
    mvc.perform(get("/refused"))
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
        .andExpect(jsonPath("$.subErrors[0].code").value("-240"))
        .andExpect(jsonPath("$.subErrors[0].message").value("Insufficient funds!"));
    mvc.perform(get("/taken")).andExpect(jsonPath("$.subErrors").doesNotExist());
  }

  @Test
  void anErrorWithoutADefinitionIsAPlain500() throws Exception {
    mvc.perform(get("/undefined"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  @Test
  void frameworkErrorsGetACodeNamedAfterTheStatus() throws Exception {
    mvc.perform(delete("/taken"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void unexpectedErrorsNeverLeakDetails() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("hunter2"))));
  }

  @Test
  void detailsSpeakTheCallersLanguage() throws Exception {
    mvc.perform(get("/boom").header("Accept-Language", "ru"))
        .andExpect(
            jsonPath("$.detail")
                .value("На нашей стороне что-то пошло не так. Попробуйте ещё раз."));
    mvc.perform(delete("/taken").header("Accept-Language", "uz-UZ"))
        .andExpect(jsonPath("$.detail").value("Bu yerda «DELETE» usuli qoʻllab-quvvatlanmaydi."));
    mvc.perform(get("/boom").header("Accept-Language", "de"))
        .andExpect(
            jsonPath("$.detail").value("Something went wrong on our side. Please try again."));
  }

  private static MockMvc mvc() {
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    var messages = new org.springframework.context.support.ResourceBundleMessageSource();
    messages.setBasename("messages/error");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    messages.setAlwaysUseMessageFormat(true);
    var advice = new ProblemDetailsAdvice(definitions());
    advice.setMessageSource(messages);
    return MockMvcBuilders.standaloneSetup(new Endpoints())
        .setControllerAdvice(advice)
        .setValidator(validator)
        .build();
  }

  /** The library's own definitions, as a service imports them, plus one of the service's own. */
  private static ErrorStarterProperties definitions() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("properties/error-exception.yaml"));
    var properties = yaml.getObject();
    properties.put("exception.starter.error.EMAIL_TAKEN.http-code", "409");
    properties.put("exception.starter.error.EMAIL_TAKEN.title", "email-taken");
    properties.put("exception.starter.error.EMAIL_TAKEN.message-en", "{0} is already registered.");
    properties.put(
        "exception.starter.error.EMAIL_TAKEN.message-uz", "{0} allaqachon roʻyxatdan oʻtgan.");
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind(ErrorConstants.PROPERTIES_PREFIX, ErrorStarterProperties.class)
        .get();
  }
}
