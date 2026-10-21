package uz.heeypay.ecom.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every failure a client can meet on a real server, Spring Security's included, is one of our
 * problem details, even with Spring Boot's own problem-details handler switched on.
 */
@SpringBootTest(
    classes = ProblemDetailsIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mvc.problemdetails.enabled=true")
class ProblemDetailsIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  record Signup(@Email String email) {}

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EnableMethodSecurity
  static class App {

    @RestController
    static class Endpoints {
      @PostMapping("/signup")
      void signup(@Valid @RequestBody Signup body) {}

      @GetMapping({"/admin/report", "/me/profile"})
      String secured() {
        return "ok";
      }

      @PreAuthorize("hasRole('ADMIN')")
      @GetMapping("/method")
      String method() {
        return "ok";
      }

      @GetMapping("/boom")
      void boom() {
        throw new IllegalStateException("db password is hunter2");
      }
    }

    /** As an app wires it: our problems answer 401 and 403. */
    @Bean
    SecurityFilterChain chain(HttpSecurity http, ProblemSecurityHandler problems) {
      return http.csrf(AbstractHttpConfigurer::disable)
          .authorizeHttpRequests(
              routes ->
                  routes
                      .requestMatchers("/admin/**")
                      .hasRole("ADMIN")
                      .requestMatchers("/me/**")
                      .authenticated()
                      .anyRequest()
                      .permitAll())
          .httpBasic(basic -> basic.authenticationEntryPoint(problems))
          .exceptionHandling(
              errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
          .build();
    }

    @Bean
    InMemoryUserDetailsManager users() {
      return new InMemoryUserDetailsManager(
          User.withUsername("partner").password("{noop}local-only").roles("PARTNER").build());
    }
  }

  @LocalServerPort int port;

  private RestTestClient client() {
    return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void malformedJsonIsABadRequest() {
    var body =
        problem(
            client()
                .post()
                .uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json")
                .exchange(),
            HttpStatus.BAD_REQUEST,
            "/signup");
    assertThat(body).containsEntry("code", "BAD_REQUEST");
  }

  @Test
  void noTokenIsA401WithABearerChallenge() {
    var response = client().get().uri("/me/profile").exchange();
    response.expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    assertThat(problem(response, HttpStatus.UNAUTHORIZED, "/me/profile"))
        .containsEntry("code", "UNAUTHORIZED");
  }

  @Test
  void theWrongRoleIsA403() {
    var basic = Base64.getEncoder().encodeToString("partner:local-only".getBytes());
    var response =
        client().get().uri("/admin/report").header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
    assertThat(problem(response.exchange(), HttpStatus.FORBIDDEN, "/admin/report"))
        .containsEntry("code", "FORBIDDEN");
  }

  @Test
  void methodSecurityRefusingTheRoleIsA403() {
    var basic = Base64.getEncoder().encodeToString("partner:local-only".getBytes());
    var response =
        client().get().uri("/method").header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
    assertThat(problem(response.exchange(), HttpStatus.FORBIDDEN, "/method"))
        .containsEntry("code", "FORBIDDEN");
  }

  @Test
  void anUnknownPathIsA404() {
    assertThat(
            problem(
                client().get().uri("/nothing-here").exchange(),
                HttpStatus.NOT_FOUND,
                "/nothing-here"))
        .containsEntry("code", "NOT_FOUND");
  }

  @Test
  void aFailureIsA500WithoutItsInternals() {
    var body =
        problem(client().get().uri("/boom").exchange(), HttpStatus.INTERNAL_SERVER_ERROR, "/boom");
    assertThat(body).containsEntry("code", "INTERNAL_ERROR");
    assertThat(body.toString()).doesNotContain("hunter2", "IllegalStateException");
  }

  /** The status, the problem media type, and a body with a code and the request as instance. */
  private static Map<String, Object> problem(
      RestTestClient.ResponseSpec response, HttpStatus status, String path) {
    response.expectStatus().isEqualTo(status);
    response.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    var body = response.expectBody(JSON).returnResult().getResponseBody();
    assertThat(body).containsKey("code").containsEntry("instance", path);
    return body;
  }
}
