rootProject.name = "error-starter"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from("uz.heeypay.ecom:version-catalog:0.1.0") } }
}
