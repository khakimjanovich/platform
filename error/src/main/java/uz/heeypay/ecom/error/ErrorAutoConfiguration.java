package uz.heeypay.ecom.error;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Adding the library is enough: every web app gets the same error format, security errors included.
 * It runs before Spring Boot's MVC configuration, so with {@code spring.mvc.problemdetails.enabled}
 * Boot's own handler backs off; an app that defines its own {@link ResponseEntityExceptionHandler}
 * replaces this one.
 */
@AutoConfiguration(beforeName = ErrorConstants.BOOT_WEB_MVC)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ErrorStarterProperties.class)
public class ErrorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
  ProblemDetailsAdvice problemDetailsAdvice(ErrorStarterProperties errors) {
    return new ProblemDetailsAdvice(errors);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(AuthenticationEntryPoint.class)
  static class SecurityProblems {

    @Bean
    @ConditionalOnMissingBean
    ProblemSecurityHandler problemSecurityHandler(
        @Qualifier(ErrorConstants.EXCEPTION_RESOLVER) HandlerExceptionResolver resolver) {
      return new ProblemSecurityHandler(resolver);
    }

    /** Only beside this library's handler: it answers through it. */
    @Bean
    @ConditionalOnMissingBean({AccessDeniedAdvice.class, ResponseEntityExceptionHandler.class})
    AccessDeniedAdvice accessDeniedAdvice(ProblemDetailsAdvice problems) {
      return new AccessDeniedAdvice(problems);
    }
  }
}
