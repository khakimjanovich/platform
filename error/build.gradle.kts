// One error format for every module: RFC 9457 problem details plus a stable error code.
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

repositories { mavenCentral() }

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.spring.webmvc)
    implementation(libs.spring.boot.autoconfigure)
    compileOnly(libs.jakarta.servlet)
    compileOnly(libs.spring.security.web) // security problems only when the app uses Spring Security
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.starter.test)
    testImplementation(libs.spring.security.web)
    testImplementation(libs.jakarta.servlet)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
