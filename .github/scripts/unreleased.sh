#!/usr/bin/env bash
# The production release plan for main. Prints "<tag> <gradle publish args>" for every artifact whose
# catalog version has no tag yet: the libraries and build-logic (`platform-<name>`, tagged
# `<name>-v<version>`), then the hub catalog (`platform`, tagged `platform-v<version>`) last, so it is
# published after what it pins. Fails when an artifact changed since the tag of its version: a
# released version is never published again, so the change needs a new version.
# Needs the tags locally (checkout with fetch-depth: 0).
set -euo pipefail

catalog=${1:-gradle/libs.versions.toml}
failed=0

# plan <tag> <gradle args> <paths that make up the artifact...>
plan() {
  local tag=$1 args=$2
  shift 2
  if ! git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
    echo "$tag $args"
  elif ! git diff --quiet "$tag" HEAD -- "$@"; then
    echo "$tag is released, but $* changed since: raise its version" >&2
    failed=1
  fi
}

hub=$(sed -n 's/^platform = "\([^"]*\)".*/\1/p' "$catalog")
if [ -z "$hub" ]; then
  echo "no hub version (platform = \"x\") in $catalog" >&2
  exit 1
fi

# Only [versions] entries (`name = "x"`); the [libraries] entries of the same name are tables.
while read -r name version; do
  if [ "$name" = build ]; then
    plan "build-logic-v$version" "-p build-logic publish" build-logic
  elif [ ! -d "$name" ]; then
    continue # in its own repository (config4j, …), which releases it; the catalog only pins it
  else
    plan "$name-v$version" ":$name:publish" "$name"
  fi
done < <(sed -n 's/^platform-\([a-z-]*\) = "\([^"]*\)".*/\1 \2/p' "$catalog")

plan "platform-v$hub" ":versioning:publish" "$catalog" versioning

exit $failed
