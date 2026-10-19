// Secrets from HashiCorp Vault (KV version 2) for any module: read only, failing closed.
plugins {
    id("ecom.library")
    id("ecom.integration-test")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.spring.web)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.jackson3.databind)

    integrationTestImplementation(libs.testcontainers.core)
    integrationTestImplementation(libs.testcontainers.junit)
}
