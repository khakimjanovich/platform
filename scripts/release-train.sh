#!/usr/bin/env bash
# release-train.sh X.Y.Z: releases the hub, then the 14 libraries in their dependency order, all at
# X.Y.Z. Each step tags the head of branch X.x and waits for its release run; a wave starts only when
# the one before it is out. Run it from the hub; the libraries are its sibling directories.
set -euo pipefail

version=${1:?usage: release-train.sh X.Y.Z}
[[ $version =~ ^([0-9]+)\.[0-9]+\.[0-9]+$ ]] || { echo "$version is not X.Y.Z" >&2; exit 1; }
branch=${BASH_REMATCH[1]}.x
tag=v$version
root=$(cd "$(dirname "$0")/../.." && pwd)

waves=(
  "platform"
  "config4j masking4j"
  "error4j persistence4j"
  "breaker4j valkey4j vault4j filestore4j outbox4j idempotency4j"
  "observability4j"
  "keycloak-admin4j"
  "security4j otp4j"
)

release() {
  local repo=$1 sha id
  cd "$root/$repo"
  git fetch -q origin "$branch" --tags
  if git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
    echo "$repo: $tag is out already"
    return 0
  fi
  sha=$(git rev-parse "origin/$branch")
  git tag "$tag" "$sha"
  git push -q origin "refs/tags/$tag"
  for _ in $(seq 1 60); do
    id=$(gh run list -R "khakimjanovich/$repo" -b "$tag" -L 1 --json databaseId -q '.[0].databaseId' 2> /dev/null || true)
    [ -n "$id" ] && break
    sleep 5
  done
  [ -n "$id" ] || { echo "$repo: no release run for $tag" >&2; return 1; }
  if gh run watch "$id" -R "khakimjanovich/$repo" --exit-status > /dev/null 2>&1; then
    echo "$repo: $tag released"
  else
    echo "$repo: release run $id failed" >&2
    return 1
  fi
}

for wave in "${waves[@]}"; do
  pids=()
  for repo in $wave; do release "$repo" & pids+=($!); done
  ok=1
  for pid in "${pids[@]}"; do wait "$pid" || ok=0; done
  [ $ok = 1 ] || { echo "stopped after: $wave" >&2; exit 1; }
done
echo "$tag: the hub and all 14 libraries are out"
