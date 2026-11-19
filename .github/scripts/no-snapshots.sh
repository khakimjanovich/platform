#!/usr/bin/env bash
# Fails when the catalog carries any -SNAPSHOT version: main only takes released versions.
set -euo pipefail

catalog=${1:-gradle/libs.versions.toml}
snapshots=$(grep -nE '"[^"]*-SNAPSHOT"' "$catalog" || true)
if [ -n "$snapshots" ]; then
  echo "main takes released versions only; drop -SNAPSHOT from:" >&2
  echo "$snapshots" >&2
  exit 1
fi
echo "no -SNAPSHOT versions"
