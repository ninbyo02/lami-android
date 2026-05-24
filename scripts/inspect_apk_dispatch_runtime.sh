#!/usr/bin/env bash
set -u

APK_PATH="${1:-}"

print_header() {
  printf '\n== %s ==\n' "$1"
}

find_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
    return
  fi
  for candidate in \
    "${ANDROID_HOME:-}/build-tools"/*/aapt \
    "${ANDROID_SDK_ROOT:-}/build-tools"/*/aapt \
    "$HOME/Android/Sdk/build-tools"/*/aapt
  do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
}

inspect_so() {
  local so_path="$1"
  print_header "Inspect $(basename "$so_path")"
  printf 'path: %s\n' "$so_path"

  if command -v file >/dev/null 2>&1; then
    file "$so_path" || true
  else
    printf 'file: unavailable\n'
  fi

  if command -v readelf >/dev/null 2>&1; then
    printf '\n-- readelf -d NEEDED --\n'
    readelf -d "$so_path" 2>/dev/null | grep -E 'NEEDED|SONAME' || true
    printf '\n-- readelf -n build id --\n'
    readelf -n "$so_path" 2>/dev/null | grep -E 'Build ID|Android|NT_VERSION|description data' || true
  else
    printf 'readelf: unavailable\n'
  fi

  if command -v nm >/dev/null 2>&1; then
    printf '\n-- nm -D LiteRt dispatch symbols --\n'
    nm -D "$so_path" 2>/dev/null | grep -iE 'LiteRtDispatchGetApi|LiteRt|Dispatch|Qualcomm' || true
  else
    printf 'nm: unavailable\n'
  fi

  if command -v strings >/dev/null 2>&1; then
    printf '\n-- strings LiteRtDispatch/Qualcomm/QNN sample --\n'
    strings "$so_path" 2>/dev/null | grep -iE 'LiteRtDispatch|Qualcomm|QNN|HTP|dispatch_api|compiler|version|SM[0-9]+' | head -120 || true
  else
    printf 'strings: unavailable\n'
  fi
}

print_header "APK dispatch runtime inspection"
if [ -z "$APK_PATH" ]; then
  printf 'usage: %s <apk path>\n' "$0"
  exit 0
fi

printf 'apk: %s\n' "$APK_PATH"
if [ ! -f "$APK_PATH" ]; then
  printf 'missing apk: %s\n' "$APK_PATH"
  exit 0
fi

print_header "APK metadata"
printf 'size: %s bytes\n' "$(wc -c < "$APK_PATH" 2>/dev/null || printf unknown)"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$APK_PATH" || true
fi

AAPT_BIN="$(find_aapt || true)"
if [ -n "$AAPT_BIN" ]; then
  printf '\n-- aapt dump badging --\n'
  "$AAPT_BIN" dump badging "$APK_PATH" 2>/dev/null | grep -E "package:|sdkVersion|targetSdkVersion|native-code" || true
else
  printf 'aapt: unavailable\n'
fi

if command -v apktool >/dev/null 2>&1; then
  printf 'apktool: available (%s)\n' "$(command -v apktool)"
else
  printf 'apktool: unavailable\n'
fi

print_header "arm64-v8a native libraries"
unzip -l "$APK_PATH" 2>/dev/null | awk '{print $4}' | grep '^lib/arm64-v8a/.*\.so$' | sort || true

print_header "dispatch/QNN/HTP/compiler candidates in APK"
unzip -l "$APK_PATH" 2>/dev/null |
  grep -iE 'dispatch|litert|qnn|htp|compiler_plugin|compiler|qualcomm' || true

WORK_DIR="${TMPDIR:-/tmp}/apk-dispatch-inspect-$(date +%s)-$$"
mkdir -p "$WORK_DIR"
unzip -q -o "$APK_PATH" 'lib/arm64-v8a/*' -d "$WORK_DIR" 2>/dev/null || true

DISPATCH_CANDIDATES="$(find "$WORK_DIR/lib/arm64-v8a" -type f -name '*.so' 2>/dev/null |
  while IFS= read -r so_path; do
    name="$(basename "$so_path")"
    lower="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')"
    case "$lower" in
      *dispatch*|*litertdispatch*|*qualcomm*dispatch*) printf '%s\n' "$so_path" ;;
    esac
  done | sort)"

print_header "dispatch runtime candidates"
if [ -z "$DISPATCH_CANDIDATES" ]; then
  printf 'missing: no arm64-v8a dispatch runtime candidate found\n'
else
  printf '%s\n' "$DISPATCH_CANDIDATES"
  while IFS= read -r so_path; do
    [ -z "$so_path" ] && continue
    inspect_so "$so_path"
  done <<EOF
$DISPATCH_CANDIDATES
EOF
fi

print_header "extracted directory"
printf '%s\n' "$WORK_DIR"

exit 0
