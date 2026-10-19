rootProject.name = "vault"

pluginManagement { includeBuild("../build-logic") }

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from("uz.heeypay.ecom:versioning:0.1.0") } }
}
