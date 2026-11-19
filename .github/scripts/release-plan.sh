#!/usr/bin/env bash
# Prints the tag main releases (v<hub version>), or nothing when that version is out already. Fails when
# the hub changed since the tag of its version: a released version is never published again.
# Needs the tags locally (checkout with fetch-depth: 0).
set -euo pipefail

catalog=gradle/libs.versions.toml
version=$(sed -n 's/^platform = "\([^"]*\)".*/\1/p' "$catalog")
[ -n "$version" ] || { echo "no hub version (platform = \"x\") in $catalog" >&2; exit 1; }

tag=v$version
if ! git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
  echo "$tag"
elif ! git diff --quiet "$tag" HEAD -- gradle version-catalog gradle-plugins build.gradle settings.gradle ':!*.md'; then
  echo "$tag is released, but the hub changed since: raise platform in $catalog" >&2
  exit 1
fi
