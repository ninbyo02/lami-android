#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/qairt242_sdk_check/$TIMESTAMP}"
QAIRT_ROOT="${1:-}"
EXPECTED_VERSION="2.42.0.251225"
KNOWN_BAD_VERSION="2.46.0.260424"

log() {
  printf '[check-qairt242] %s\n' "$*"
}

die() {
  log "ERROR: $*"
  printf 'ERROR: %s\n' "$*" >>"$OUT_DIR/ERROR.txt"
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/check_qairt242_sdk.sh /path/to/qairt/2.42.0.251225

Checks that the given QAIRT root looks like the exact 2.42.0.251225 SDK used
by Radxa public QAIRT documentation. This script does not build LiteRT, does
not install an app, does not run Engine.initialize, and does not copy files
into app source sets.
EOF
}

metadata_for() {
  local file="$1"
  local out="$2"
  {
    printf 'path=%s\n' "$file"
    if [ ! -e "$file" ]; then
      printf 'present=false\n'
      return
    fi
    printf 'present=true\n'
    if [ -f "$file" ]; then
      printf 'size='
      wc -c <"$file" 2>/dev/null || true
      printf 'sha256='
      sha256sum "$file" 2>/dev/null | awk '{print $1}' || true
      printf 'file='
      file "$file" 2>/dev/null || true
      printf '\nBuild ID:\n'
      readelf -n "$file" 2>/dev/null | sed -n '/Build ID/p' || true
      printf '\nSONAME/NEEDED:\n'
      readelf -d "$file" 2>/dev/null | grep -E 'SONAME|NEEDED' || true
      printf '\nFiltered strings:\n'
      strings "$file" 2>/dev/null |
        grep -Ei 'QNN|QAIRT|HTP|V79|V75|V73|V68|2\.42|251225|version|build|sdk' |
        sort -u |
        head -200 || true
    fi
  } >"$out"
}

if [ -z "$QAIRT_ROOT" ] || [ "$QAIRT_ROOT" = "--help" ] || [ "$QAIRT_ROOT" = "-h" ]; then
  usage
  exit 2
fi

mkdir -p "$OUT_DIR/metadata"
: >"$OUT_DIR/ERROR.txt"

if [ ! -d "$QAIRT_ROOT" ]; then
  die "QAIRT root does not exist: $QAIRT_ROOT"
fi

QAIRT_REALPATH="$(readlink -f "$QAIRT_ROOT" 2>/dev/null || printf '%s' "$QAIRT_ROOT")"

{
  printf 'QAIRT_ROOT=%s\n' "$QAIRT_ROOT"
  printf 'QAIRT_REALPATH=%s\n' "$QAIRT_REALPATH"
  printf 'EXPECTED_VERSION=%s\n' "$EXPECTED_VERSION"
  printf 'is_symlink=%s\n' "$([ -L "$QAIRT_ROOT" ] && printf true || printf false)"
  printf 'Safety: no build, no app install, no Engine.initialize, no app jniLibs writes.\n'
} >"$OUT_DIR/environment.txt"

case "$QAIRT_REALPATH" in
  *"$KNOWN_BAD_VERSION"*)
    die "QAIRT root resolves to known 2.46 SDK/overlay: $QAIRT_REALPATH"
    ;;
esac

case "$QAIRT_REALPATH" in
  *"$EXPECTED_VERSION"*) ;;
  *)
    die "QAIRT root path does not contain exact $EXPECTED_VERSION: $QAIRT_REALPATH"
    ;;
esac

REQUIRED_FILES=(
  "bin/envsetup.sh"
  "bin/x86_64-linux-clang/qnn-net-run"
  "bin/x86_64-linux-clang/qnn-platform-validator"
  "lib/aarch64-android/libQnnSystem.so"
  "lib/aarch64-android/libQnnHtp.so"
  "lib/aarch64-android/libQnnHtpPrepare.so"
)

OPTIONAL_FILES=(
  "lib/aarch64-android/libQnnHtpV68Stub.so"
  "lib/hexagon-v68/unsigned/libQnnHtpV68Skel.so"
  "lib/aarch64-android/libQnnHtpV73Stub.so"
  "lib/hexagon-v73/unsigned/libQnnHtpV73Skel.so"
  "lib/aarch64-android/libQnnHtpV79Stub.so"
  "lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so"
)

printf 'kind\trelative_path\tpresent\tpath\n' >"$OUT_DIR/file_presence.tsv"
for rel in "${REQUIRED_FILES[@]}"; do
  path="$QAIRT_ROOT/$rel"
  present=false
  [ -e "$path" ] && present=true
  printf 'required\t%s\t%s\t%s\n' "$rel" "$present" "$path" >>"$OUT_DIR/file_presence.tsv"
  if [ "$present" != true ]; then
    printf '%s\n' "$rel" >>"$OUT_DIR/missing_required_files.txt"
  fi
done

for rel in "${OPTIONAL_FILES[@]}"; do
  path="$QAIRT_ROOT/$rel"
  present=false
  [ -e "$path" ] && present=true
  printf 'optional\t%s\t%s\t%s\n' "$rel" "$present" "$path" >>"$OUT_DIR/file_presence.tsv"
done

if [ -s "$OUT_DIR/missing_required_files.txt" ]; then
  die "required QAIRT files are missing; see $OUT_DIR/missing_required_files.txt"
fi

for rel in "${REQUIRED_FILES[@]}" "${OPTIONAL_FILES[@]}"; do
  case "$rel" in
    *.so)
      metadata_for "$QAIRT_ROOT/$rel" "$OUT_DIR/metadata/$(basename "$rel").txt"
      ;;
  esac
done

{
  printf '# QAIRT 2.42 SDK Check Summary\n\n'
  printf -- '- QAIRT root: `%s`\n' "$QAIRT_ROOT"
  printf -- '- Real path: `%s`\n' "$QAIRT_REALPATH"
  printf -- '- Expected version: `%s`\n' "$EXPECTED_VERSION"
  printf -- '- Required files: `present`\n'
  printf -- '- Rejected 2.46 overlay: `yes`\n'
  printf -- '- Purpose: Radxa public-generation comparison, not primary SM8750/V79 proof\n'
  printf -- '- LiteRT build: `not run`\n'
  printf -- '- App install: `not run`\n'
  printf -- '- Engine.initialize: `not run`\n'
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"
