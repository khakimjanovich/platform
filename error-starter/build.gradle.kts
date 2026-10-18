// One error format for every module: RFC 9457 problem details plus a stable error code (episode 02).
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
}

publishing { publications { create<MavenPublication>("starter") { from(components["java"]) } } }
