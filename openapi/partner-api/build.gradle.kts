// The partner contract (episode 03): partner-v1.yaml → generated Spring interfaces and models, published as a jar.
// Generated code lives only in build/ and in the published jar, never in source.
plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.openapi.generator)
}

group = "uz.heeypay.ecom"
version = libs.versions.ecom.platform.get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    withSourcesJar()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.spring.webmvc)
    api(libs.jakarta.validation)
    api(libs.jakarta.annotation)
    api(libs.jackson.databind)
    compileOnly(libs.jakarta.servlet) // provided by the app's web server
}

val spec = "$projectDir/src/main/openapi/partner-v1.yaml"
val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(spec)
    outputDir.set(generatedDir.map { it.asFile.path })
    apiPackage.set("uz.heeypay.ecom.partner.api")
    modelPackage.set("uz.heeypay.ecom.partner.api.model")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "useTags" to "true",
            "skipDefaultInterface" to "true",
            "openApiNullable" to "false",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
        ),
    )
}

sourceSets.main { java.srcDir(generatedDir.map { it.dir("src/main/java") }) }
tasks.compileJava { dependsOn(tasks.openApiGenerate) }
tasks.named("sourcesJar") { dependsOn(tasks.openApiGenerate) }

// The spec itself ships in the jar, so Swagger UI can show exactly what we wrote.
tasks.processResources { from("src/main/openapi") { into("static/openapi") } }

publishing { publications { create<MavenPublication>("api") { from(components["java"]) } } }
