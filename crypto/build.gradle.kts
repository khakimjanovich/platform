// Encryption for data that must never be readable in the database: AES-256-GCM with key ids and
// rotation, and a keyed blind index to find equal values without decrypting them.
plugins {
    id("ecom.library")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.autoconfigure)
}
