// A module's API contract: `<module>-api` holds `src/main/openapi/<module>-v1.yaml`; Spring server
// interfaces and models are generated into build/ and published in the jar, never into source.
// The spec ships in the jar too, so Swagger UI shows exactly what we wrote.
plugins {
    id("ecom.library")
    id("org.openapi.generator")
}

val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

val module = project.name.removeSuffix("-api")
val generatedDir = layout.buildDirectory.dir("generated/openapi")

dependencies {
    "api"(platform(lib("spring-boot-bom")))
    "api"(lib("spring-webmvc"))
    "api"(lib("jakarta-validation"))
    "api"(lib("jakarta-annotation"))
    "api"(lib("jackson-databind"))
    "compileOnly"(lib("jakarta-servlet")) // provided by the app's web server
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/openapi/$module-v1.yaml")
    outputDir.set(generatedDir.map { it.asFile.path })
    apiPackage.set("uz.heeypay.ecom.$module.api")
    modelPackage.set("uz.heeypay.ecom.$module.api.model")
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

sourceSets.named("main") { java.srcDir(generatedDir.map { it.dir("src/main/java") }) }
tasks.named("compileJava") { dependsOn(tasks.named("openApiGenerate")) }
tasks.named("sourcesJar") { dependsOn(tasks.named("openApiGenerate")) }
tasks.named<ProcessResources>("processResources") { from("src/main/openapi") { into("static/openapi") } }
