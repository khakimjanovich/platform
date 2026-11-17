package uz.khakimjanovich.platform.buildlogic;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
import com.tngtech.archunit.library.GeneralCodingRules;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class ArchitectureRules {

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

  private static final DescribedPredicate<JavaClass> UTILITY_CLASS =
      describe(
          "a *Utils or *Util class",
          c ->
              (c.getSimpleName().endsWith("Utils") || c.getSimpleName().endsWith("Util"))
                  && !c.isAnonymousClass()
                  && !c.isMemberClass()
                  && !c.isInterface()
                  && !c.isEnum());

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
              .allowEmptyShould(true),
          noFields()
              .should(GeneralCodingRules.BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION)
              .because("dependencies must be injected via constructors, never fields")
              .allowEmptyShould(true),
          noClasses()
              .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
              .because(
                  "production code must use structured logging (SLF4J), never System.out,"
                      + " System.err, or printStackTrace")
              .allowEmptyShould(true),
          noClasses()
              .should(GeneralCodingRules.USE_JAVA_UTIL_LOGGING)
              .because("production code must use SLF4J, never java.util.logging")
              .allowEmptyShould(true),
          slices()
              .matching("uz.khakimjanovich.platform.(*)..")
              .should()
              .beFreeOfCycles()
              .because("packages must be free of cyclic dependencies")
              .allowEmptyShould(true),
          classes()
              .that(CONSTANT_CLASS)
              .should()
              .haveModifier(JavaModifier.FINAL)
              .andShould(haveOnlyPrivateConstructors())
              .because("a constant class is final and cannot be instantiated")
              .allowEmptyShould(true),
          classes()
              .that(UTILITY_CLASS)
              .should()
              .haveModifier(JavaModifier.FINAL)
              .andShould(haveOnlyPrivateConstructors())
              .andShould(haveOnlyStaticMethods())
              .andShould(haveOnlyStaticFields())
              .because("a utility class is final with a private constructor and only static members")
              .allowEmptyShould(true));

  // What @EnableModulePersistence builds for a module.
  private static final Set<String> MODULE_PERSISTENCE =
      Set.of(
          "com.zaxxer.hikari.HikariDataSource",
          "liquibase.integration.spring.SpringLiquibase",
          "org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean");

  private static final Set<String> JSON_TREE_BUILDERS =
      Set.of(
          "tools.jackson.databind.node.ObjectNode",
          "tools.jackson.databind.node.ArrayNode",
          "tools.jackson.databind.node.JsonNodeFactory",
          "com.fasterxml.jackson.databind.node.ObjectNode",
          "com.fasterxml.jackson.databind.node.ArrayNode",
          "com.fasterxml.jackson.databind.node.JsonNodeFactory");

  private static final String GENERAL_EXCEPTION = "uz.khakimjanovich.platform.error.GeneralException";

  private static final Set<String> COMPILER_THROWABLES =
      Set.of("java.lang.MatchException", "java.lang.IncompatibleClassChangeError");

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
              .resideOutsideOfPackages("..mapper..", "..config..", "..model..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("tools.jackson.databind..", "com.fasterxml.jackson.databind..")
              .because(
                  "JSON is read into a DTO in model by a mapper in mapper; a mapper in mapper maps a"
                      + " response")
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
              .or()
              .areAnnotatedWith("org.springframework.stereotype.Controller")
              .should()
              .haveSimpleNameEndingWith("Controller")
              .andShould()
              .resideInAPackage("..controller..")
              .because(
                  "controllers are annotated with @RestController or @Controller, reside in"
                      + " controller and end with Controller")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith("org.springframework.stereotype.Service")
              .should()
              .resideInAPackage("..service..")
              .because("a service lives in service")
              .allowEmptyShould(true),
          classes()
              .that()
              .areAnnotatedWith("org.springframework.stereotype.Repository")
              .or()
              .haveSimpleNameEndingWith("Repository")
              .should()
              .resideInAPackage("..repository..")
              .because("a repository lives in repository")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..repository..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..controller..", "..service..")
              .because("a repository never depends on services or controllers")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..repository..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("org.springframework.web..")
              .because("repositories must not depend on web layer classes")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..service..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..controller..")
              .because("services must never depend on the presentation layer")
              .allowEmptyShould(true),
          noClasses()
              .that()
              .resideInAPackage("..controller..")
              .should()
              .beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
              .orShould()
              .beAnnotatedWith("jakarta.transaction.Transactional")
              .because("transactions belong in the service layer, never in controllers")
              .allowEmptyShould(true),
          noMethods()
              .that()
              .areDeclaredInClassesThat()
              .resideInAPackage("..controller..")
              .should()
              .beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
              .orShould()
              .beAnnotatedWith("jakarta.transaction.Transactional")
              .because("transactions belong in the service layer, never in controllers")
              .allowEmptyShould(true),
          slices()
              .matching(
                  "..(controller|service|repository|config|property|model|client|mapper|workflow)..")
              .should()
              .beFreeOfCycles()
              .because("service layers must be free of cyclic dependencies")
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
              .allowEmptyShould(true),
          noClasses()
              .should()
              .dependOnClassesThat(
                  describe(
                      "a module's pool, migrations or entity manager (HikariDataSource,"
                          + " SpringLiquibase, LocalContainerEntityManagerFactoryBean)",
                      (JavaClass c) -> MODULE_PERSISTENCE.contains(c.getName())))
              .because("@EnableModulePersistence builds them from the platform")
              .allowEmptyShould(true));

  private ArchitectureRules() {}

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

  private static ArchCondition<JavaClass> haveOnlyPrivateConstructors() {
    return new ArchCondition<>("have only private constructors") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.getConstructors().isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  item, item.getFullName() + " has no declared constructors"));
          return;
        }
        for (var ctor : item.getConstructors()) {
          if (!ctor.getModifiers().contains(JavaModifier.PRIVATE)) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    item.getFullName()
                        + " has a non-private constructor: "
                        + ctor.getFullName()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> haveOnlyStaticMethods() {
    return new ArchCondition<>("have only static methods") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (var method : item.getMethods()) {
          if (!method.getModifiers().contains(JavaModifier.STATIC)
              && !method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    item.getFullName()
                        + " has a non-static method: "
                        + method.getFullName()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> haveOnlyStaticFields() {
    return new ArchCondition<>("have only static fields") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (var field : item.getFields()) {
          if (!field.getModifiers().contains(JavaModifier.STATIC)
              && !field.getModifiers().contains(JavaModifier.SYNTHETIC)) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    item.getFullName()
                        + " has a non-static field: "
                        + field.getFullName()));
          }
        }
      }
    };
  }

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
