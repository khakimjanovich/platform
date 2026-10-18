package uz.heeypay.ecom.error;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Adding the library is enough: every web app gets the same error format, security errors included.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ProblemDetailsAdvice.class)
public class ErrorAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(AuthenticationEntryPoint.class)
  static class SecurityProblems {

    @Bean
    @ConditionalOnMissingBean
    ProblemSecurityHandler problemSecurityHandler(
        @Qualifier(ErrorConstantUtils.EXCEPTION_RESOLVER) HandlerExceptionResolver resolver) {
      return new ProblemSecurityHandler(resolver);
    }
  }
}
