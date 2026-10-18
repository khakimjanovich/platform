rootProject.name = "openapi"

// One project per module contract: the OpenAPI spec in, a generated API jar out (published to local Maven).
include("partner-api")

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from("uz.heeypay.ecom:version-catalog:0.1.0") } }
}
