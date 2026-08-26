#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

mapfile -t tracked_native_binaries < <(git ls-files -- '*.so')

if (( ${#tracked_native_binaries[@]} > 0 )); then
  echo "Tracked native binaries are forbidden." >&2
  echo "Stage licensed/local runtime files from an external SDK path at build time." >&2
  printf ' - %s\n' "${tracked_native_binaries[@]}" >&2
  exit 1
fi

echo "tracked_native_binaries=none"
