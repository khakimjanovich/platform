rootProject.name = "build-logic"

// Convention plugins shared by every ecom build: platform libraries and the app's modules.
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from("uz.heeypay.ecom:versioning:0.1.0") } }
}
