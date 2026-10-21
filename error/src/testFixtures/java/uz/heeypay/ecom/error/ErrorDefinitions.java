package uz.heeypay.ecom.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * The check every service runs in a unit test: each error any of its libraries or modules can throw
 * has a definition (an HTTP status, a title, and a message in English, Russian and Uzbek with the
 * same placeholders), and no definition is left over.
 *
 * <pre>{@code
 * @Test
 * void everyErrorIsDefinedInEveryLanguage() {
 *   ErrorDefinitions.assertComplete("uz.heeypay.ecom");
 * }
 * }</pre>
 */
public final class ErrorDefinitions {

  /** Where libraries, modules and adapters keep their definitions. */
  public static final String DEFINITIONS = "classpath*:properties/*-exception.yaml";

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

  private ErrorDefinitions() {}

  /** Every {@link StatusThrowable} enum under the package against every definition file. */
  public static void assertComplete(String basePackage) {
    assertComplete(basePackage, DEFINITIONS);
  }

  /** The same, against the definition files matching {@code locationPattern}. */
  public static void assertComplete(String basePackage, String locationPattern) {
    var definitions = definitions(locationPattern).error();
    var thrown = new TreeSet<String>();
    for (var status : statuses(basePackage)) {
      var type = status.getType();
      thrown.add(type);
      var definition = definitions.get(type);
      assertThat(definition).as(type).isNotNull();
      assertThat(definition.httpCode()).as(type).isBetween(400, 599);
      assertThat(definition.title()).as(type).isNotBlank();
      for (var message :
          new String[] {definition.messageEn(), definition.messageRu(), definition.messageUz()}) {
        assertThat(message).as(type).isNotBlank();
        assertThat(placeholders(message)).as(type).isEqualTo(placeholders(definition.messageEn()));
      }
    }
    assertThat(definitions.keySet()).as("definitions nobody throws").isSubsetOf(thrown);
  }

  private static ErrorStarterProperties definitions(String locationPattern) {
    try {
      var yaml = new YamlPropertiesFactoryBean();
      yaml.setResources(new PathMatchingResourcePatternResolver().getResources(locationPattern));
      return new Binder(new MapConfigurationPropertySource(yaml.getObject()))
          .bind(ErrorConstants.PROPERTIES_PREFIX, ErrorStarterProperties.class)
          .orElseGet(() -> new ErrorStarterProperties(Map.of()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Every constant of every StatusThrowable enum under the package. */
  private static List<StatusThrowable> statuses(String basePackage) {
    // An enum whose constants have bodies compiles to an abstract class, which the default
    // filter (concrete classes only) would skip.
    var scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return definition.getMetadata().isIndependent();
          }
        };
    scanner.addIncludeFilter(new AssignableTypeFilter(StatusThrowable.class));
    var statuses = new ArrayList<StatusThrowable>();
    for (var candidate : scanner.findCandidateComponents(basePackage)) {
      var type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
      if (type.isEnum()) {
        statuses.addAll(Arrays.asList((StatusThrowable[]) type.getEnumConstants()));
      }
    }
    assertThat(statuses).as("StatusThrowable enums under " + basePackage).isNotEmpty();
    return statuses;
  }

  private static TreeSet<String> placeholders(String message) {
    var found = new TreeSet<String>();
    var matcher = PLACEHOLDER.matcher(message);
    while (matcher.find()) found.add(matcher.group());
    return found;
  }
}
