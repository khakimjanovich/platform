#!/usr/bin/env bash
# release-version.sh <tag> <line>: prints the release version of tag vX.Y.Z, after checking that it
# belongs to the branch line (1.x-SNAPSHOT takes 1.Y.Z only) and that the tagged commit is on X.x.
# Needs the branches locally (checkout with fetch-depth: 0).
set -euo pipefail

tag=$1 line=$2
version=${tag#v}
[[ $version =~ ^([0-9]+)\.[0-9]+\.[0-9]+$ ]] || { echo "$tag is not vX.Y.Z" >&2; exit 1; }
major=${BASH_REMATCH[1]}
[ "$line" = "$major.x-SNAPSHOT" ] || { echo "$tag is not on the line $line" >&2; exit 1; }
git merge-base --is-ancestor HEAD "origin/$major.x" || { echo "$tag is not on branch $major.x" >&2; exit 1; }
echo "$version"
