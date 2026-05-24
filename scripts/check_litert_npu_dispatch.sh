#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

EXACT_NAME="libLiteRtDispatch_Qualcomm.so"
DISPATCH_PATTERNS=(
  "libLiteRtDispatch_Qualcomm.so"
  "libLiteRtDispatchQualcomm.so"
  "liblitert_dispatch_qualcomm.so"
  "libLiteRtDispatch.so"
  "liblitert_dispatch.so"
  "*dispatch*.so"
  "*Dispatch*.so"
)

FOUND=0
FOUND_FILESYSTEM=0
FOUND_AAR=0
FOUND_APK=0

print_header() {
  printf '\n== %s ==\n' "$1"
}

sha256_for_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" 2>/dev/null | awk '{print $1}'
  else
    printf 'sha256sum-unavailable'
  fi
}

sha256_for_zip_entry() {
  local zip_path="$1"
  local entry="$2"
  if command -v sha256sum >/dev/null 2>&1; then
    unzip -p "$zip_path" "$entry" 2>/dev/null | sha256sum | awk '{print $1}'
  else
    printf 'sha256sum-unavailable'
  fi
}

print_path_match() {
  local path="$1"
  local sha
  sha="$(sha256_for_file "$path")"
  FOUND=1
  if [ "$(basename "$path")" = "$EXACT_NAME" ]; then
    printf 'FOUND exact: %s\n' "$path"
  else
    printf 'FOUND candidate: %s\n' "$path"
  fi
  printf '  sha256: %s\n' "$sha"
}

search_dir() {
  local label="$1"
  local dir="$2"
  print_header "$label"
  printf 'search dir: %s\n' "$dir"
  if [ -z "$dir" ] || [ ! -d "$dir" ]; then
    printf 'SKIP: directory not found\n'
    return
  fi

  local tmp
  tmp="$(mktemp)"
  local pattern
  for pattern in "${DISPATCH_PATTERNS[@]}"; do
    find "$dir" -type f -iname "$pattern" -print 2>/dev/null >> "$tmp"
  done

  if [ ! -s "$tmp" ]; then
    printf 'no dispatch .so candidate found\n'
    rm -f "$tmp"
    return
  fi

  while IFS= read -r path; do
    print_path_match "$path"
  done < <(sort -u "$tmp")
  FOUND=1
  FOUND_FILESYSTEM=1
  rm -f "$tmp"
}

is_relevant_aar() {
  local path_lower
  path_lower="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "$path_lower" in
    *litert*|*genai*|*mediapipe*|*ai-edge*|*llm*|*lm*|*qnn*|*qualcomm*) return 0 ;;
    *) return 1 ;;
  esac
}

zip_entries_matching_dispatch() {
  local zip_path="$1"
  unzip -l "$zip_path" 2>/dev/null |
    awk '{print $4}' |
    grep -E '(^|/)[^/]*([dD]ispatch)[^/]*\.so$|(^|/)libLiteRtDispatch[^/]*\.so$' || true
}

zip_arm64_lib_entries() {
  local zip_path="$1"
  unzip -l "$zip_path" 2>/dev/null |
    awk '{print $4}' |
    grep -E '^(jni|lib)/arm64-v8a/.*\.so$' |
    sort || true
}

search_aars() {
  print_header "AAR contents"
  if ! command -v unzip >/dev/null 2>&1; then
    printf 'SKIP: unzip not available\n'
    return
  fi

  local tmp_aars
  tmp_aars="$(mktemp)"
  if [ -d "${HOME:-}/.gradle/caches" ]; then
    find "${HOME:-}/.gradle/caches" -type f -name '*.aar' -print 2>/dev/null |
      while IFS= read -r aar; do
        if is_relevant_aar "$aar"; then
          printf '%s\n' "$aar"
        fi
      done | sort -u > "$tmp_aars"
  fi

  if [ ! -s "$tmp_aars" ]; then
    printf 'no relevant AAR found under Gradle cache\n'
    rm -f "$tmp_aars"
    return
  fi

  while IFS= read -r aar; do
    printf 'aar: %s\n' "$aar"
    local native_entries
    native_entries="$(zip_arm64_lib_entries "$aar")"
    if [ -z "$native_entries" ]; then
      printf '  no arm64 native .so entry\n'
    else
      printf '  arm64 native .so entries:\n'
      printf '%s\n' "$native_entries" | sed 's/^/    /'
    fi

    local dispatch_entries
    dispatch_entries="$(zip_entries_matching_dispatch "$aar")"
    if [ -z "$dispatch_entries" ]; then
      printf '  no dispatch .so entry\n'
    else
      while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        printf '  FOUND AAR entry: %s\n' "$entry"
        printf '    sha256: %s\n' "$(sha256_for_zip_entry "$aar" "$entry")"
      done <<< "$dispatch_entries"
      FOUND=1
      FOUND_AAR=1
    fi
  done < "$tmp_aars"
  rm -f "$tmp_aars"
}

search_apks() {
  print_header "APK contents"
  if ! command -v unzip >/dev/null 2>&1; then
    printf 'SKIP: unzip not available\n'
    return
  fi

  local tmp_apks
  tmp_apks="$(mktemp)"
  find "$ROOT_DIR/app/build/outputs" "$ROOT_DIR/app/build/intermediates" "$ROOT_DIR/build" \
    -type f -name '*.apk' -print 2>/dev/null | sort -u > "$tmp_apks"

  if [ ! -s "$tmp_apks" ]; then
    printf 'no APK found under app/build or build\n'
    rm -f "$tmp_apks"
    return
  fi

  while IFS= read -r apk; do
    printf 'apk: %s\n' "$apk"
    local native_entries
    native_entries="$(zip_arm64_lib_entries "$apk")"
    if [ -z "$native_entries" ]; then
      printf '  no arm64 native .so entry\n'
    else
      printf '  arm64 native .so entries:\n'
      printf '%s\n' "$native_entries" | sed 's/^/    /'
    fi

    local dispatch_entries
    dispatch_entries="$(zip_entries_matching_dispatch "$apk")"
    if [ -z "$dispatch_entries" ]; then
      printf '  no arm64 dispatch .so entry\n'
    else
      while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        printf '  FOUND APK entry: %s\n' "$entry"
        printf '    sha256: %s\n' "$(sha256_for_zip_entry "$apk" "$entry")"
      done <<< "$dispatch_entries"
      FOUND=1
      FOUND_APK=1
    fi
  done < "$tmp_apks"
  rm -f "$tmp_apks"
}

print_header "LiteRT Qualcomm dispatch API search"
printf 'repo root: %s\n' "$ROOT_DIR"
printf 'exact expected name: %s\n' "$EXACT_NAME"
printf 'candidate patterns: %s\n' "${DISPATCH_PATTERNS[*]}"

search_dir "Gradle cache direct files" "${HOME:-}/.gradle/caches"
search_aars
search_dir "app/build" "$ROOT_DIR/app/build"
search_dir "build" "$ROOT_DIR/build"
search_dir "ANDROID_HOME" "${ANDROID_HOME:-}"
search_dir "ANDROID_SDK_ROOT" "${ANDROID_SDK_ROOT:-}"
search_dir "QAIRT_HOME" "${QAIRT_HOME:-}"
search_dir "QAIRT_ROOT" "${QAIRT_ROOT:-}"
search_dir "/workspace/sdk/qairt" "/workspace/sdk/qairt"
search_dir "workspace/sdk/qairt" "$ROOT_DIR/workspace/sdk/qairt"
search_dir "local_sdks" "$ROOT_DIR/local_sdks"
search_dir "third_party" "$ROOT_DIR/third_party"
search_apks

print_header "Summary"
printf 'filesystem dispatch candidates: %s\n' "$([ "$FOUND_FILESYSTEM" -eq 1 ] && printf found || printf not-found)"
printf 'AAR dispatch candidates: %s\n' "$([ "$FOUND_AAR" -eq 1 ] && printf found || printf not-found)"
printf 'APK dispatch candidates: %s\n' "$([ "$FOUND_APK" -eq 1 ] && printf found || printf not-found)"

if [ "$FOUND" -eq 0 ]; then
  printf '\nNOT FOUND: LiteRT Qualcomm dispatch API .so\n'
  printf 'summary: AAR=no APK=no filesystem=no\n'
  exit 2
fi

printf '\nFOUND: LiteRT Qualcomm dispatch API .so candidate(s)\n'
exit 0
