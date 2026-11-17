#!/usr/bin/env bash
# Prints "<tag> <gradle publish args>" for every artifact whose catalog version has no tag yet: the
# libraries and build-logic (`platform-<name>`, tagged `<name>-v<version>`), then the hub catalog
# (`platform`, tagged `platform-v<version>`) last, so it is published after what it pins.
set -euo pipefail

catalog=${1:-gradle/libs.versions.toml}
tagged() { git ls-remote --exit-code --tags origin "refs/tags/$1" > /dev/null; }

# Only [versions] entries (`name = "x"`); the [libraries] entries of the same name are tables.
sed -n 's/^platform-\([a-z-]*\) = "\([^"]*\)".*/\1 \2/p' "$catalog" | while read -r name version; do
  if [ "$name" = build ]; then
    tag="build-logic-v$version" args="-p build-logic publish"
  else
    tag="$name-v$version" args=":$name:publish"
  fi
  tagged "$tag" || echo "$tag $args"
done

hub=$(sed -n 's/^platform = "\([^"]*\)".*/\1/p' "$catalog")
tagged "platform-v$hub" || echo "platform-v$hub :versioning:publish"
