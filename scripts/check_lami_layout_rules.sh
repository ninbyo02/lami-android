#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TARGETS=(
  "app/src/main/java/com/sonusid/ollama/ui/screens/settings"
  "app/src/main/java/com/sonusid/ollama/ui/screens/spriteeditor"
)

fail_count=0
warn_count=0

print_section() {
  printf '\n[%s] %s\n' "$1" "$2"
}

increment_fail() {
  fail_count=$((fail_count + 1))
}

increment_warn() {
  warn_count=$((warn_count + 1))
}

require_rg() {
  if ! command -v rg >/dev/null 2>&1; then
    echo "[ERROR] rg (ripgrep) is required but was not found in PATH."
    exit 2
  fi
  echo "[OK] rg found"
}

collect_kotlin_files() {
  local target
  local found=0
  KOTLIN_FILES=()
  for target in "${TARGETS[@]}"; do
    if [ ! -d "$ROOT_DIR/$target" ]; then
      echo "[ERROR] target directory not found: $target"
      exit 2
    fi
    found=1
    while IFS= read -r file; do
      KOTLIN_FILES+=("$file")
    done < <(find "$ROOT_DIR/$target" -type f -name '*.kt' | sort)
  done

  if [ "$found" -eq 1 ]; then
    echo "[OK] target directories found"
  fi
}

print_matches() {
  local matches="$1"
  if [ -n "$matches" ]; then
    printf '%s\n' "$matches"
  fi
}

rg_code_matches() {
  local pattern="$1"
  local file
  local output=""
  for file in "${KOTLIN_FILES[@]}"; do
    local file_output
    file_output=$(rg -n -H --color never "$pattern" "$file" 2>/dev/null || true)
    if [ -n "$file_output" ]; then
      file_output=$(printf '%s\n' "$file_output" | sed "s#${ROOT_DIR}/##" | awk -F: 'match($0, /^[^:]+:[0-9]+:/) { text=substr($0, RLENGTH+1); if (text !~ /^[[:space:]]*(\/\/|\/\*|\*|\*\/)/) print $0 }')
      if [ -n "$file_output" ]; then
        if [ -n "$output" ]; then
          output+=$'\n'
        fi
        output+="$file_output"
      fi
    fi
  done
  printf '%s' "$output"
}

run_forbidden_check() {
  local pattern="$1"
  local label="$2"
  local matches
  matches=$(rg_code_matches "$pattern")
  if [ -n "$matches" ]; then
    print_section "FAIL" "$label"
    print_matches "$matches"
    increment_fail
  fi
}

run_presence_check() {
  local label="$1"
  shift
  local combined=""
  local pattern
  for pattern in "$@"; do
    local matches
    matches=$(rg_code_matches "$pattern")
    if [ -n "$matches" ]; then
      combined="$matches"
      break
    fi
  done

  if [ -n "$combined" ]; then
    echo "[OK] $label"
  else
    print_section "FAIL" "$label"
    echo "No matches found in target Kotlin files."
    increment_fail
  fi
}

run_warning_check() {
  local label="$1"
  local matches="$2"
  if [ -n "$matches" ]; then
    print_section "WARN" "$label"
    print_matches "$matches"
    increment_warn
  fi
}

find_zero_spacer_nearby() {
  local file
  local output=""
  for file in "${KOTLIN_FILES[@]}"; do
    local file_output
    file_output=$(awk '
      { lines[NR] = $0 }
      END {
        for (nr = 1; nr <= NR; nr++) {
          if (lines[nr] ~ /Spacer\(/) {
            start = nr - 3
            if (start < 1) start = 1
            end = nr + 3
            for (i = start; i <= end; i++) {
              if (lines[i] ~ /0\.dp/) {
                path = FILENAME
                sub(/^.*\/workspace\/lami-android\//, "", path)
                print path ":" nr ":" lines[nr]
                break
              }
            }
          }
        }
      }
    ' "$file")
    if [ -n "$file_output" ]; then
      if [ -n "$output" ]; then
        output+=$'\n'
      fi
      output+="$file_output"
    fi
  done
  printf '%s' "$output"
}

main() {
  echo "== Lami layout rule check: settings-family screens =="
  cd "$ROOT_DIR"

  collect_kotlin_files
  require_rg

  echo
  run_presence_check "Found explicit zero contentWindowInsets" \
    'contentWindowInsets[[:space:]]*=[[:space:]]*WindowInsets\([[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*\)' \
    'contentWindowInsets[[:space:]]*=[[:space:]]*WindowInsets\([[:space:]]*left[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*top[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*right[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*bottom[[:space:]]*=[[:space:]]*0[[:space:]]*\)'
  run_presence_check "Found explicit zero TopBar windowInsets" \
    'windowInsets[[:space:]]*=[[:space:]]*WindowInsets\([[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*\)' \
    'windowInsets[[:space:]]*=[[:space:]]*WindowInsets\([[:space:]]*left[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*top[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*right[[:space:]]*=[[:space:]]*0[[:space:]]*,[[:space:]]*bottom[[:space:]]*=[[:space:]]*0[[:space:]]*\)'
  run_presence_check "Found consumeWindowInsets usage" 'consumeWindowInsets\('

  run_forbidden_check 'statusBarsPadding\(' 'Forbidden status bar handling found: statusBarsPadding('
  run_forbidden_check 'windowInsetsPadding\([[:space:]]*WindowInsets\.statusBars' 'Forbidden status bar handling found: windowInsetsPadding(WindowInsets.statusBars'
  run_forbidden_check 'TopAppBarDefaults\.windowInsets' 'Forbidden status bar handling found: TopAppBarDefaults.windowInsets'
  run_forbidden_check 'WindowInsets\.statusBars' 'Forbidden status bar handling found: WindowInsets.statusBars'

  run_warning_check 'Possible no-op spacing hooks: Spacer(...) near 0.dp' "$(find_zero_spacer_nearby)"
  run_warning_check 'Possible no-op spacing hooks: padding(top = 0.dp)' "$(rg_code_matches 'padding\([[:space:]]*top[[:space:]]*=[[:space:]]*0\.dp\)')"
  run_warning_check 'Possible no-op spacing hooks: offset(y = 0.dp)' "$(rg_code_matches 'offset\([[:space:]]*y[[:space:]]*=[[:space:]]*0\.dp\)')"
  run_warning_check 'Possible no-op spacing hooks: absoluteOffset(y = 0.dp)' "$(rg_code_matches 'absoluteOffset\([[:space:]]*y[[:space:]]*=[[:space:]]*0\.dp\)')"

  echo
  if [ "$fail_count" -gt 0 ]; then
    echo "Result: FAILED"
    exit 1
  fi

  if [ "$warn_count" -gt 0 ]; then
    echo "Result: WARNINGS"
    exit 0
  fi

  echo "Result: OK"
}

main "$@"
