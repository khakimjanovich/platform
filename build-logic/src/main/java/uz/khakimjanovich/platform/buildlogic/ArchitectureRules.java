package uz.khakimjanovich.platform.buildlogic;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
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
              .allowEmptyShould(true));

  private ArchitectureRules() {}

  /**
   * The violations in the compiled classes of {@code classDirs} whose source is in {@code sources};
   * empty when the project keeps the architecture.
   */
  public static List<String> violations(Collection<File> classDirs, File sources) {
    var all =
        new ClassFileImporter()
            .importPaths(classDirs.stream().filter(File::isDirectory).map(File::toPath).toList());
    JavaClasses ours = all.that(fromSources(sources.toPath()));
    var found = new ArrayList<String>();
    for (var rule : RULES) {
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
