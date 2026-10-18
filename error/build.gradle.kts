// One error format for every module: RFC 9457 problem details plus a stable error code.
plugins {
    id("ecom.library")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.spring.webmvc)
    implementation(libs.spring.boot.autoconfigure)
    compileOnly(libs.jakarta.servlet)
    compileOnly(libs.spring.security.web) // security problems only when the app uses Spring Security

    testImplementation(libs.starter.webmvc)
    testImplementation(libs.starter.validation)
    testImplementation(libs.spring.security.web)
}
