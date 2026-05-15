#!/usr/bin/env bash
set -u

LEFT="${1:-}"
RIGHT="${2:-}"

print_header() {
  printf '\n== %s ==\n' "$1"
}

usage() {
  printf 'usage: %s <left apk|lib dir> <right apk|lib dir>\n' "$0"
}

extract_input() {
  local input="$1"
  local label="$2"
  local out_dir="$3"

  mkdir -p "$out_dir"
  if [ -f "$input" ] && printf '%s' "$input" | grep -qi '\.apk$'; then
    unzip -q -o "$input" 'lib/arm64-v8a/*' -d "$out_dir/apk" 2>/dev/null || true
    local apk_lib_dir="$out_dir/apk/lib/arm64-v8a"
    if [ -d "$apk_lib_dir" ]; then
      printf '%s\n' "$apk_lib_dir"
      return
    fi
  fi

  if [ -d "$input" ]; then
    if [ -d "$input/lib/arm64-v8a" ]; then
      printf '%s\n' "$input/lib/arm64-v8a"
      return
    fi
    if [ -d "$input/arm64-v8a" ]; then
      printf '%s\n' "$input/arm64-v8a"
      return
    fi
    printf '%s\n' "$input"
    return
  fi

  printf '%s: missing input %s\n' "$label" "$input" >&2
  printf '%s\n' "$out_dir/missing"
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  fi
}

soname_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -1
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

export_count_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -Ws "$file" 2>/dev/null |
      awk '$4 == "FUNC" && ($5 == "GLOBAL" || $5 == "WEAK") && $7 != "UND" {count++} END {print count+0}'
  fi
}

has_symbol() {
  local file="$1"
  local symbol="$2"
  if command -v nm >/dev/null 2>&1 && [ -f "$file" ]; then
    if nm -D "$file" 2>/dev/null | grep -q "$symbol"; then
      printf 'yes'
    else
      printf 'no'
    fi
  else
    printf 'unknown'
  fi
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  fi
}

elf_class_for() {
  local file="$1"
  if command -v file >/dev/null 2>&1 && [ -f "$file" ]; then
    file -b "$file" | sed 's/[[:space:]]\+/ /g'
  fi
}

risk_for() {
  local lib="$1"
  local left_build_id="$2"
  local right_build_id="$3"
  local left_present="$4"
  local right_present="$5"
  case "$lib" in
    libLiteRtDispatch_Qualcomm.so)
      if [ "$left_present" = "yes" ] && [ "$right_present" = "no" ]; then
        printf 'high'
      elif [ "$left_build_id" = "$right_build_id" ] && [ -n "$left_build_id" ]; then
        printf 'low'
      else
        printf 'high'
      fi
      ;;
    libLiteRt.so|liblitertlm_jni.so)
      if [ "$left_present" = "yes" ] && [ "$right_present" = "yes" ] && [ "$left_build_id" = "$right_build_id" ] && [ -n "$left_build_id" ]; then
        printf 'low'
      elif [ "$left_present" = "yes" ] && [ "$right_present" = "yes" ]; then
        printf 'high'
      else
        printf 'medium'
      fi
      ;;
    libQnn*.so|libQnnHtp*.so|libQnnSystem.so|libQnnTFLiteDelegate.so)
      if [ "$left_present" = "yes" ] && [ "$right_present" = "yes" ] && [ "$left_build_id" = "$right_build_id" ] && [ -n "$left_build_id" ]; then
        printf 'low'
      elif [ "$left_present" = "yes" ] && [ "$right_present" = "yes" ]; then
        printf 'medium'
      else
        printf 'medium'
      fi
      ;;
    *)
      printf 'unknown'
      ;;
  esac
}

row_for() {
  local lib="$1"
  local source="$2"
  local file="$3"
  local present="no"
  [ -f "$file" ] && present="yes"
  local sha=""
  local build_id=""
  local elf_class=""
  local soname=""
  local needed=""
  local exports=""
  local dispatch_get_api="no"
  local qualcomm_options_get="no"
  if [ "$present" = "yes" ]; then
    sha="$(sha_for "$file")"
    build_id="$(build_id_for "$file")"
    elf_class="$(elf_class_for "$file")"
    soname="$(soname_for "$file")"
    needed="$(needed_for "$file")"
    exports="$(export_count_for "$file")"
    dispatch_get_api="$(has_symbol "$file" "LiteRtDispatchGetApi")"
    qualcomm_options_get="$(has_symbol "$file" "LiteRtQualcommOptionsGet")"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$source" \
    "$lib" \
    "$present" \
    "${file:-none}" \
    "${sha:-none}" \
    "${build_id:-none}" \
    "${elf_class:-none}" \
    "${soname:-none}" \
    "${needed:-none}" \
    "${exports:-0}" \
    "$dispatch_get_api" \
    "$qualcomm_options_get"
}

if [ -z "$LEFT" ] || [ -z "$RIGHT" ]; then
  usage
  exit 0
fi

WORK_DIR="${TMPDIR:-/tmp}/native-lib-compare-$(date +%s)-$$"
LEFT_DIR="$(extract_input "$LEFT" left "$WORK_DIR/left")"
RIGHT_DIR="$(extract_input "$RIGHT" right "$WORK_DIR/right")"

print_header "Native library comparison"
printf 'left: %s\n' "$LEFT"
printf 'left arm64 dir: %s\n' "$LEFT_DIR"
printf 'right: %s\n' "$RIGHT"
printf 'right arm64 dir: %s\n' "$RIGHT_DIR"

LIBS="$(find "$LEFT_DIR" "$RIGHT_DIR" -maxdepth 1 -type f -name '*.so' 2>/dev/null |
  sed 's#.*/##' |
  grep -E '^(libLiteRt\.so|liblitertlm_jni\.so|libLiteRtDispatch_Qualcomm\.so|libQnn.*\.so|libQnnHtp.*\.so|libQnnSystem\.so|libQnnTFLiteDelegate\.so)$' |
  sort -u)"

print_header "Per-source matrix"
printf 'source\tlibrary\tpresent\tpath\tsha256\tbuild_id\telf_class\tsoname\tneeded\texported_symbols\tLiteRtDispatchGetApi\tLiteRtQualcommOptionsGet\n'
while IFS= read -r lib; do
  [ -z "$lib" ] && continue
  row_for "$lib" "left" "$LEFT_DIR/$lib"
  row_for "$lib" "right" "$RIGHT_DIR/$lib"
done <<EOF
$LIBS
EOF

print_header "Compatibility summary"
printf 'library\tleft_build_id\tright_build_id\tleft_present\tright_present\tabi_risk\tnotes\n'
while IFS= read -r lib; do
  [ -z "$lib" ] && continue
  left_file="$LEFT_DIR/$lib"
  right_file="$RIGHT_DIR/$lib"
  left_present="no"
  right_present="no"
  [ -f "$left_file" ] && left_present="yes"
  [ -f "$right_file" ] && right_present="yes"
  left_build_id="$(build_id_for "$left_file")"
  right_build_id="$(build_id_for "$right_file")"
  risk="$(risk_for "$lib" "$left_build_id" "$right_build_id" "$left_present" "$right_present")"
  notes=""
  if [ "$lib" = "libLiteRtDispatch_Qualcomm.so" ] && [ "$left_present" != "$right_present" ]; then
    notes="dispatch present only on one side; do not copy without matched LiteRT stack"
  elif [ "$left_present" = "yes" ] && [ "$right_present" = "yes" ] && [ "$left_build_id" != "$right_build_id" ]; then
    notes="build id differs"
  else
    notes="see per-source matrix"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$lib" "${left_build_id:-none}" "${right_build_id:-none}" "$left_present" "$right_present" "$risk" "$notes"
done <<EOF
$LIBS
EOF

print_header "String probes"
for side in left right; do
  dir="$LEFT_DIR"
  [ "$side" = "right" ] && dir="$RIGHT_DIR"
  for lib in libLiteRtDispatch_Qualcomm.so libLiteRt.so liblitertlm_jni.so; do
    file="$dir/$lib"
    [ -f "$file" ] || continue
    printf '\n-- %s %s --\n' "$side" "$lib"
    if command -v strings >/dev/null 2>&1; then
      strings "$file" 2>/dev/null | grep -iE 'LiteRtDispatchGetApi|LiteRtQualcommOptionsGet|dispatch api|Qualcomm Dispatch|QNN API|SM8750|compiler_plugin|compiler plugin' | head -80 || true
    else
      printf 'strings unavailable\n'
    fi
  done
done

print_header "work directory"
printf '%s\n' "$WORK_DIR"

exit 0
