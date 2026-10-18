// One-time codes for any module: stored as a hash in Redis, with a lifetime, an attempt limit and a send limit.
plugins {
    `java-library`
    `maven-publish`
}

group = "uz.heeypay.ecom"
version = libs.versions.ecom.platform.get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    withSourcesJar()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.ecom.error)
    api(libs.starter.data.redis)
    implementation(libs.spring.boot.autoconfigure)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations["integrationTestImplementation"].extendsFrom(configurations.implementation.get())
dependencies {
    "integrationTestImplementation"(platform(libs.spring.boot.bom))
    "integrationTestImplementation"(libs.starter.test)
    "integrationTestImplementation"(libs.testcontainers.core)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestRuntimeOnly"(libs.junit.launcher)
}
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against real Redis (Testcontainers)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
}
tasks.check { dependsOn(integrationTestTask) }

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
