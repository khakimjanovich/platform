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
import org.springframework.http.HttpStatus;
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

  @RestController
  static class Endpoints {
    @PostMapping("/signup")
    void signup(@Valid @RequestBody Signup body) {}

    @GetMapping("/taken")
    void taken() {
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Already registered");
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
  void ownErrorsKeepTheirCode() throws Exception {
    mvc.perform(get("/taken"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
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
    var advice = new ProblemDetailsAdvice();
    advice.setMessageSource(messages);
    return MockMvcBuilders.standaloneSetup(new Endpoints())
        .setControllerAdvice(advice)
        .setValidator(validator)
        .build();
  }
}
