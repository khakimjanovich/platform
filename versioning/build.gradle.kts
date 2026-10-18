// The one version catalog, published to local Maven so every build uses the same versions.
plugins {
    `version-catalog`
    `maven-publish`
}

group = "uz.heeypay.ecom"
version = "0.1.0"

catalog { versionCatalog { from(files("libs.versions.toml")) } }

publishing {
    publications { create<MavenPublication>("catalog") { from(components["versionCatalog"]) } }
}
