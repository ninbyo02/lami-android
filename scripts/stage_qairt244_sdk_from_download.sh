#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT="${1:-}"
DEST="${QAIRT244_DEST:-$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225}"
EXPECTED_VERSION="2.44.0.260225"
KNOWN_BAD_VERSION="2.46.0.260424"

log() {
  printf '[stage-qairt244] %s\n' "$*"
}

die() {
  log "ERROR: $*"
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/v2.44.0.260225.zip
  bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/qairt/2.44.0.260225

Safely stages an officially obtained QAIRT 2.44.0.260225 SDK into:
  ~/compose/qairt/workspace/sdk/qairt/2.44.0.260225

The script refuses to overwrite an existing destination, rejects paths that
resolve to a known 2.46 SDK/overlay, and runs check_qairt244_sdk.sh after
staging. It does not build LiteRT, does not install an app, and does not copy
anything into app/src/**/jniLibs.
EOF
}

find_candidate_root() {
  local base="$1"
  if [ -d "$base" ]; then
    if [ -e "$base/bin/envsetup.sh" ] || [ -d "$base/lib/aarch64-android" ]; then
      printf '%s\n' "$base"
      return 0
    fi
    find "$base" -maxdepth 6 -type d \( -path "*$EXPECTED_VERSION*" -o -iname '*qairt*' -o -iname '*QNN*' \) 2>/dev/null |
      while IFS= read -r d; do
        if [ -e "$d/bin/envsetup.sh" ] || [ -d "$d/lib/aarch64-android" ]; then
          printf '%s\n' "$d"
          return 0
        fi
      done |
      head -1
  fi
}

if [ -z "$INPUT" ] || [ "$INPUT" = "--help" ] || [ "$INPUT" = "-h" ]; then
  usage
  exit 2
fi

if [ ! -e "$INPUT" ]; then
  die "input does not exist: $INPUT"
fi

if [ -e "$DEST" ]; then
  die "destination already exists; refusing to overwrite: $DEST"
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/qairt244_stage.XXXXXX")"
cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

INPUT_REALPATH="$(readlink -f "$INPUT" 2>/dev/null || printf '%s' "$INPUT")"
case "$INPUT_REALPATH" in
  *"$KNOWN_BAD_VERSION"*)
    die "input resolves to known 2.46 SDK/overlay: $INPUT_REALPATH"
    ;;
esac

case "$INPUT" in
  *.zip|*.ZIP)
    log "extracting zip to temporary directory"
    command -v unzip >/dev/null 2>&1 || die "unzip is required for zip input"
    unzip -q "$INPUT" -d "$WORK_DIR/extracted" || die "failed to extract zip"
    CANDIDATE="$(find_candidate_root "$WORK_DIR/extracted")"
    ;;
  *)
    [ -d "$INPUT" ] || die "non-zip input must be a directory: $INPUT"
    CANDIDATE="$(find_candidate_root "$INPUT")"
    ;;
esac

if [ -z "${CANDIDATE:-}" ] || [ ! -d "$CANDIDATE" ]; then
  die "could not find a QAIRT SDK root inside input"
fi

CANDIDATE_REALPATH="$(readlink -f "$CANDIDATE" 2>/dev/null || printf '%s' "$CANDIDATE")"
case "$CANDIDATE_REALPATH" in
  *"$KNOWN_BAD_VERSION"*)
    die "candidate resolves to known 2.46 SDK/overlay: $CANDIDATE_REALPATH"
    ;;
esac

case "$CANDIDATE_REALPATH" in
  *"$EXPECTED_VERSION"*) ;;
  *)
    if ! find "$CANDIDATE" -maxdepth 3 -type f 2>/dev/null | grep -E "$EXPECTED_VERSION|260225" >/dev/null; then
      die "candidate does not look like exact $EXPECTED_VERSION: $CANDIDATE_REALPATH"
    fi
    ;;
esac

PARENT="$(dirname "$DEST")"
mkdir -p "$PARENT"
log "copying SDK to $DEST"
cp -a "$CANDIDATE" "$DEST" || die "failed to copy SDK to $DEST"

log "running SDK sanity check"
bash "$ROOT_DIR/scripts/check_qairt244_sdk.sh" "$DEST"
