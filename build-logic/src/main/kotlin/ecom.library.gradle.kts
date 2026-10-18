// A platform library: published to local Maven, with sources, under the platform's group and version.
plugins {
    id("ecom.java")
    `maven-publish`
}

val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "uz.heeypay.ecom"
version = libs.findVersion("ecom-platform").get().requiredVersion

java { withSourcesJar() }

publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
