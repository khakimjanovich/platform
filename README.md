# platform

The libraries and API contracts every ecom service builds on, in one Gradle build. The app and each
adapter include it (`includeBuild`), so Gradle builds these projects in place of their
`uz.heeypay.ecom:*` coordinates; nothing is published to run them.

## Versions

- One version for the whole platform, `ecom-platform` in `gradle/libs.versions.toml` (the catalog
  every build reads), following Semantic Versioning 2.0.
- A PR that removes or changes a public class, method, property or error code bumps the minor
  version while it is 0.x (the major from 1.0); one that only adds bumps the minor too; a fix that
  changes no API bumps the patch.
- A release is the tag `platform-vX.Y.Z` on `main`. Publishing to a package repository waits for the
  platform to become its own repository.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).
