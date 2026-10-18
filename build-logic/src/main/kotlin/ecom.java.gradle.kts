// Every Java build: Java from the catalog, the Spring Boot BOM, Lombok, JUnit 5,
// parameter names for Spring binding, and one code format checked by `check`.
plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion))
}

// Keep parameter names so Spring can bind query and request parameters by name.
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

dependencies {
    "implementation"(platform(lib("spring-boot-bom")))
    "testImplementation"(platform(lib("spring-boot-bom")))
    "testImplementation"(lib("starter-test"))
    "testRuntimeOnly"(lib("junit-launcher"))
    "compileOnly"(lib("lombok"))
    "annotationProcessor"(lib("lombok"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
    }
}
