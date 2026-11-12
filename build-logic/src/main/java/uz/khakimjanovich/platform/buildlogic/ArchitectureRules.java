package uz.khakimjanovich.platform.buildlogic;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The architecture every service on the platform shares, checked by platform.java's {@code
 * verifyArchitecture} on the classes of {@code src/main/java} (generated code is left out):
 *
 * <ul>
 *   <li>Layers: a controller calls services, never repositories, service implementations,
 *       entities, mappers, workflows or configuration; only a service implementation, a workflow
 *       or configuration reaches a repository; nothing calls a controller; a controller serves its
 *       contract and is never the contract's client.
 *   <li>Constants: a constant (a static final field of a value type: a primitive, a string, a
 *       number, a duration, a pattern, a date format, or a set, list or map of values) lives in a {@code
 *       ConstantUtils} or {@code *Constants} class, apart from the code that uses it; such a class
 *       holds only static final fields.
 *   <li>Packages, in a service only (a library is one flat package), as in the Humo and Uzcard
 *       adapters: a {@code @RestController} in {@code
 *       controller}, a {@code @Configuration} in {@code config}, a {@code @ConfigurationProperties}
 *       class in {@code property}, a {@code *Request}, {@code *Response} or {@code *Params} DTO in
 *       {@code model}, data only (no controller, service, client, configuration, property,
 *       repository or mapper), and a {@code *ExceptionStatus} in {@code constant}.
 *   <li>Mapping, in a service only: a service never touches JSON (a mapper in {@code mapper} maps
 *       a response), a controller passes the request to a service and returns its result (no
 *       client, mapper or JSON), and a mapper only maps (no service or controller).
 *   <li>Outgoing calls, in a service only: an {@code @HttpExchange} interface in {@code client}
 *       holds the paths; a service never builds a request with RestClient, RestTemplate or
 *       WebClient.
 *   <li>No {@code Map<String, Object>} as a field, a parameter or a return type, and no {@code
 *       MultiValueMap}: a body or a structure is a DTO (a form is typed {@code @RequestParam}s).
 *   <li>Name, value pairs, in a service only: no method takes a {@code String...} or {@code
 *       Object...} (pairs to build a body from), and nothing outside {@code mapper} builds a JSON
 *       tree ({@code ObjectNode}, {@code ArrayNode}, {@code JsonNodeFactory}).
 *   <li>Errors, in a service only: outside {@code config} and {@code property} (which fail at
 *       startup, before any request) the only throwable a class creates is the error library's
 *       {@code GeneralException} or a subclass of it: a problem detail with a stable code. A case that cannot happen is
 *       {@code GeneralException(INTERNAL_ERROR)}.
 * </ul>
 *
 * A rule whose classes a project does not have (a library with no controller) passes.
 */
public final class ArchitectureRules {

  /** Types whose static final fields are values: what a constant is. */
  private static final Set<String> VALUE_TYPES =
      Set.of(
          "java.lang.String",
          "java.lang.Boolean",
          "java.lang.Character",
          "java.lang.Byte",
          "java.lang.Short",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Float",
          "java.lang.Double",
          "java.math.BigDecimal",
          "java.math.BigInteger",
          "java.time.Duration",
          "java.time.Period",
          "java.time.format.DateTimeFormatter",
          "java.util.regex.Pattern",
          "java.util.Set",
          "java.util.List",
          "java.util.Map");

  private static final DescribedPredicate<JavaClass> CONSTANT_CLASS =
      describe(
          "a ConstantUtils or *Constants class",
          c ->
              c.getSimpleName().equals("ConstantUtils") || c.getSimpleName().endsWith("Constants"));

  private static final DescribedPredicate<JavaField> A_CONSTANT =
      describe(
          "a static final field of a value type",
          f ->
              f.getModifiers().contains(JavaModifier.STATIC)
                  && f.getModifiers().contains(JavaModifier.FINAL)
                  && !f.getModifiers().contains(JavaModifier.SYNTHETIC)
                  && !f.getName().equals("serialVersionUID")
                  && !f.getOwner().isEnum()
                  && isValue(f.getType()));

  private static final List<ArchRule> RULES =
      List.of(
          noClasses()
              .that()
              .resideInAPackage("..controller..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "..repository..",
                  "..service.impl..",
                  "..entity..",
                  "..mapper..",
                  "..workflow..",
                  "..config..")
              .because("a controller calls services")
              .allowEmptyShould(true),
          classes()
              .that()
              .resideInAPackage("..repository..")
              .should()
              .onlyHaveDependentClassesThat()
              .resideInAnyPackage(
                  "..repository..", "..service.impl..", "..workflow..", "..config..")
              .because("only a service implementation, a workflow or configuration reaches data")
              .allowEmptyShould(true),
          classes()
              .that()
              .resideInAPackage("..controller..")
              .should()
              .onlyHaveDependentClassesThat()
              .resideInAPackage("..controller..")
              .because("nothing calls a controller")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..controller..")
              .should()
              .implement(
                  describe(
                      "a contract's client",
                      (JavaClass type) -> type.getSimpleName().endsWith("Client")))
              .because("a controller serves its contract, it is never the contract's client")
              .allowEmptyShould(true),
          fields()
              .that(A_CONSTANT)
              .should()
              .beDeclaredInClassesThat(CONSTANT_CLASS)
              .because("constants are kept apart from the code that uses them")
              .allowEmptyShould(true),
          fields()
              .that()
              .areDeclaredInClassesThat(CONSTANT_CLASS)
              .should()
              .beStatic()
              .andShould()
              .beFinal()
              .because("a constant class holds only constants")
              .allowEmptyShould(true),
          noFields()
              .should(haveUntypedMapType())
              .because("a body or a structure is a DTO in model, never a Map<String, Object>")
              .allowEmptyShould(true),
          noMethods()
              .should(takeOrReturnUntypedMap())
              .because("a body or a structure is a DTO in model, never a Map<String, Object>")
              .allowEmptyShould(true),
          noClasses()
              .should()
              .dependOnClassesThat()
              .haveFullyQualifiedName("org.springframework.util.MultiValueMap")
              .orShould()
              .dependOnClassesThat()
              .haveFullyQualifiedName("org.springframework.util.LinkedMultiValueMap")
              .because(
                  "a body is a DTO, a form is typed @RequestParams: never a map of name, value pairs")
              .allowEmptyShould(true));

  /** Jackson's JSON tree builders, in both Jackson packages. */
  private static final Set<String> JSON_TREE_BUILDERS =
      Set.of(
          "tools.jackson.databind.node.ObjectNode",
          "tools.jackson.databind.node.ArrayNode",
          "tools.jackson.databind.node.JsonNodeFactory",
          "com.fasterxml.jackson.databind.node.ObjectNode",
          "com.fasterxml.jackson.databind.node.ArrayNode",
          "com.fasterxml.jackson.databind.node.JsonNodeFactory");

  /** The error library's exception: a problem detail with a stable code. */
  private static final String GENERAL_EXCEPTION = "uz.khakimjanovich.platform.error.GeneralException";

  /** The throwables the compiler puts in an exhaustive switch: not ours. */
  private static final Set<String> COMPILER_THROWABLES =
      Set.of("java.lang.MatchException", "java.lang.IncompatibleClassChangeError");

  /**
   * A new throwable other than GeneralException. A class the build cannot see the hierarchy of (a
   * library's) counts when its name says it is an exception or an error. A private constructor's
   * guard (Lombok's @UtilityClass: "this class is never made") is not an error path and passes, nor
   * is what the compiler puts in an exhaustive switch. A subclass of GeneralException is one.
   */
  private static final DescribedPredicate<JavaConstructorCall> NEW_OTHER_THROWABLE =
      describe(
          "a throwable other than GeneralException",
          call -> {
            var origin = call.getOrigin();
            if (origin.isConstructor() && origin.getModifiers().contains(JavaModifier.PRIVATE)) {
              return false;
            }
            var owner = call.getTargetOwner();
            // A GeneralException or its subclass builds its own superclass: super(...).
            var from = origin.getOwner();
            if (origin.isConstructor()
                && from.isAssignableTo(GENERAL_EXCEPTION)
                && from.getRawSuperclass().map(owner::equals).orElse(false)) {
              return false;
            }
            if (COMPILER_THROWABLES.contains(owner.getName())) {
              return false;
            }
            if (owner.isAssignableTo(GENERAL_EXCEPTION)
                || owner.getName().equals(GENERAL_EXCEPTION)) {
              return false;
            }
            var name = owner.getSimpleName();
            return owner.isAssignableTo(Throwable.class)
                || name.endsWith("Exception")
                || name.endsWith("Error");
          });

  /** A service's packages, as in the Humo and Uzcard adapters; a library is one flat package. */
  private static final List<ArchRule> SERVICE_RULES =
      List.of(
          noClasses()
              .that()
              .resideInAPackage("..service..")
              .should()
              .dependOnClassesThat(
                  describe(
                      "an HTTP client of its own (RestClient, RestTemplate, WebClient)",
                      (JavaClass c) ->
                          c.getName().equals("org.springframework.web.client.RestClient")
                              || c.getName().startsWith("org.springframework.web.client.RestClient$")
                              || c.getName().equals("org.springframework.web.client.RestTemplate")
                              || c.getName()
                                  .startsWith(
                                      "org.springframework.web.reactive.function.client.WebClient")))
              .because("an outgoing call is an @HttpExchange interface in client, its paths in one place")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith("org.springframework.web.service.annotation.HttpExchange")
              .should()
              .resideInAPackage("..client..")
              .because("an outgoing call is an @HttpExchange interface in client")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..service..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("tools.jackson.databind..", "com.fasterxml.jackson.databind..")
              .because("a service returns its result; a mapper in mapper maps a response")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..controller..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "..client..",
                  "..mapper..",
                  "tools.jackson.databind..",
                  "com.fasterxml.jackson.databind..")
              .because("a controller passes the request to a service and returns its result")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..mapper..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..service..", "..controller..")
              .because("a mapper only maps")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
              .should()
              .resideInAPackage("..controller..")
              .because("a controller lives in controller")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith("org.springframework.context.annotation.Configuration")
              .should()
              .resideInAPackage("..config..")
              .because("configuration lives in config")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith(
                  "org.springframework.boot.context.properties.ConfigurationProperties")
              .should()
              .resideInAPackage("..property..")
              .because("a service's settings live in property")
              .allowEmptyShould(true),
          classes()
              .that()
              .haveSimpleNameEndingWith("Request")
              .or()
              .haveSimpleNameEndingWith("Response")
              .or()
              .haveSimpleNameEndingWith("Params")
              .should()
              .resideInAPackage("..model..")
              .because("what goes over the wire is a DTO in model")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..model..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "..controller..",
                  "..service..",
                  "..client..",
                  "..config..",
                  "..property..",
                  "..repository..",
                  "..mapper..")
              .because("a DTO is data only")
              .allowEmptyShould(true),
          noMethods()
              .should(takeNameValuePairs())
              .because("a body is a DTO or typed parameters, never built from name, value pairs")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideOutsideOfPackage("..mapper..")
              .should()
              .dependOnClassesThat(
                  describe(
                      "a JSON tree builder (ObjectNode, ArrayNode, JsonNodeFactory)",
                      (JavaClass c) -> JSON_TREE_BUILDERS.contains(c.getName())))
              .because("a body is a DTO; only a mapper reshapes a JSON tree")
              .allowEmptyShould(true),
          classes()
              .that()
              .haveSimpleNameEndingWith("ExceptionStatus")
              .should()
              .resideInAPackage("..constant..")
              .because("error codes live in constant")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideOutsideOfPackages("..config..", "..property..")
              .should()
              .callConstructorWhere(NEW_OTHER_THROWABLE)
              .because(
                  "a service throws GeneralException, a problem detail with a stable code; only"
                      + " config and property, which fail at startup, throw others")
              .allowEmptyShould(true));

  private ArchitectureRules() {}

  /**
   * The violations in the compiled classes of {@code classDirs} whose source is in {@code sources},
   * with a service's package rules when {@code service}; empty when the project keeps the
   * architecture.
   */
  public static List<String> violations(
      Collection<File> classDirs, File sources, boolean service) {
    var all =
        new ClassFileImporter()
            .importPaths(classDirs.stream().filter(File::isDirectory).map(File::toPath).toList());
    JavaClasses ours = all.that(fromSources(sources.toPath()));
    var found = new ArrayList<String>();
    var rules = new ArrayList<>(RULES);
    if (service) {
      rules.addAll(SERVICE_RULES);
    }
    for (var rule : rules) {
      var result = rule.evaluate(ours);
      if (result.hasViolation()) {
        found.add(result.getFailureReport().toString());
      }
    }
    return found;
  }

  /** A value: a primitive (or an array of them), a listed type, or a collection of values. */
  private static boolean isValue(JavaType type) {
    var raw = type.toErasure();
    if (raw.isPrimitive() || (raw.isArray() && raw.getBaseComponentType().isPrimitive())) {
      return true;
    }
    if (!VALUE_TYPES.contains(raw.getName())) {
      return false;
    }
    return !(type instanceof JavaParameterizedType parameterized)
        || parameterized.getActualTypeArguments().stream().allMatch(ArchitectureRules::isValue);
  }

  /** {@code Map<String, Object>} (or {@code Map<String, ?>}): a hand-built structure. */
  private static boolean isUntypedMap(JavaType type) {
    if (!(type instanceof JavaParameterizedType map)
        || !map.toErasure().isAssignableTo("java.util.Map")
        || map.getActualTypeArguments().size() != 2) {
      return false;
    }
    var key = map.getActualTypeArguments().get(0);
    var value = map.getActualTypeArguments().get(1);
    return key.toErasure().getName().equals("java.lang.String")
        && value.toErasure().getName().equals("java.lang.Object")
        && (!(value instanceof JavaWildcardType wildcard)
            || wildcard.getLowerBounds().isEmpty());
  }


  private static ArchCondition<JavaField> haveUntypedMapType() {
    return new ArchCondition<>("have the type Map<String, Object>") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        if (isUntypedMap(field.getType())) {
          events.add(SimpleConditionEvent.satisfied(field, field.getFullName() + " is a Map<String, Object>"));
        }
      }
    };
  }

  private static ArchCondition<JavaMethod> takeOrReturnUntypedMap() {
    return new ArchCondition<>("take or return a Map<String, Object>") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (isUntypedMap(method.getReturnType())
            || method.getParameterTypes().stream().anyMatch(ArchitectureRules::isUntypedMap)) {
          events.add(SimpleConditionEvent.satisfied(method, method.getFullName() + " uses a Map<String, Object>"));
        }
      }
    };
  }

  /**
   * A last parameter of {@code String[]} or {@code Object[]} (a {@code String...} or {@code
   * Object...}): name, value pairs to build a body from. {@code main(String[])} and code the
   * compiler made are left out.
   */
  private static ArchCondition<JavaMethod> takeNameValuePairs() {
    return new ArchCondition<>("take a String... or Object...") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        var parameters = method.getRawParameterTypes();
        if (parameters.isEmpty()
            || method.getName().equals("main")
            || method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
          return;
        }
        var last = parameters.get(parameters.size() - 1);
        if (last.isArray()
            && Set.of("java.lang.String", "java.lang.Object")
                .contains(last.getComponentType().getName())) {
          events.add(
              SimpleConditionEvent.satisfied(
                  method, method.getFullName() + " takes name, value pairs"));
        }
      }
    };
  }

  /** A class whose .java is in src/main/java: generated code (build/generated) is not ours. */
  private static DescribedPredicate<JavaClass> fromSources(Path sources) {
    return describe(
        "compiled from src/main/java",
        c ->
            c.getSource()
                .flatMap(source -> source.getFileName())
                .map(name -> Files.exists(sources.resolve(c.getPackageName().replace('.', '/')).resolve(name)))
                .orElse(false));
  }
}
