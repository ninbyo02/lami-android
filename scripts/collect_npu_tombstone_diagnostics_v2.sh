#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="${APP_ID:-io.github.ninbyo02.lami.npu}"
LABEL="${LABEL:-npu}"
RUN_ID="${RUN_ID:-}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR=""

KEYWORDS="gallerynpu|customnpu|LiteRt|LiteRT|litert|liblitertlm|libLiteRt|libLiteRtDispatch|Dispatch|dispatch|QNN|Qnn|HTP|Htp|NPU|nativeCreateEngine|No usable Dispatch runtime found|Failed to initialize Dispatch API|insufficient capabilities|capabilities|LiteRtRuntimeCApi|LiteRtDispatchCheckRuntimeCompatibility|CheckRuntimeCompatibility|RuntimeCApi|QNN manager|ADSP|LD_LIBRARY_PATH|dlopen|linker|cannot locate|library not found|symbol not found|version mismatch|FATAL|SIGABRT|Abort message|tombstone|DEBUG|QAIRT244_SMOKE|QAIRT244_SENTINEL|QAIRT244_DIAG|qairt244_app_jni_smoke_v1|qairt244_jni_entry_v1|qairt244_android_log_v1|qairt244_native_file_v1"
LIBS=(
  "liblitertlm_jni.so"
  "libLiteRt.so"
  "libLiteRtDispatch_Qualcomm.so"
  "libQnnSystem.so"
  "libQnnHtp.so"
  "libQnnHtpPrepare.so"
  "libQnnHtpV79Stub.so"
  "libQnnHtpV79Skel.so"
  "libLiteRtRuntimeCApi.so"
  "libllm_inference_engine_jni.so"
)

usage() {
  cat <<'EOF'
Usage:
  collect_npu_tombstone_diagnostics.sh [--app-id APP_ID] [--label LABEL] [--run-id RUN_ID] [--output-dir DIR]

Defaults:
  --app-id io.github.ninbyo02.lami.npu
  --label npu
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --app-id)
      APP_ID="${2:?Missing --app-id value}"
      shift 2
      ;;
    --label)
      LABEL="${2:?Missing --label value}"
      shift 2
      ;;
    --run-id)
      RUN_ID="${2:?Missing --run-id value}"
      shift 2
      ;;
    --output-dir)
      OUT_DIR="${2:?Missing --output-dir value}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$OUT_DIR" ]; then
  OUT_DIR="artifacts/npu_diagnostics/${TIMESTAMP}_${LABEL}"
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[npu-tombstone-collect] %s\n' "$*"
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  fi
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

soname_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -n 1
  fi
}

write_missing_or_file() {
  local app_file="$1"
  local out="$2"
  if ! adb shell run-as "$APP_ID" cat "$app_file" >"$out" 2>"$out.err"; then
    printf '<missing>\n' >"$out"
  fi
}

context_extract() {
  local pattern="$1"
  local file="$2"
  local out="$3"
  if [ -s "$file" ]; then
    grep -n -F -B80 -A320 "$pattern" "$file" >"$out" 2>/dev/null || printf '<missing>\n' >"$out"
  else
    printf '<missing>\n' >"$out"
  fi
}

decode_register_ascii() {
  local input="$1"
  local output="$2"
  perl -ne '
    while (/(x\d+)\s+([0-9a-fA-F]{16})/g) {
      my ($reg, $hex) = ($1, $2);
      my $text = "";
      for (my $i = length($hex) - 2; $i >= 0; $i -= 2) {
        my $v = hex(substr($hex, $i, 2));
        $text .= ($v >= 32 && $v <= 126) ? chr($v) : ".";
      }
      print "$reg\t$hex\t$text\n" if $text =~ /[A-Za-z0-9][A-Za-z0-9 ][A-Za-z0-9]/;
    }
  ' "$input" >"$output" 2>/dev/null || true
}

pick_local_apk() {
  case "$APP_ID" in
    *customnpu) printf 'app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk' ;;
    *gallerynpu) printf 'app/build/outputs/apk/galleryStackExperiment/debug/app-galleryStackExperiment-debug.apk' ;;
    *.npu) printf 'app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk' ;;
    *) printf 'app/build/outputs/apk/standard/debug/app-standard-debug.apk' ;;
  esac
}

if ! command -v adb >/dev/null 2>&1; then
  log "adb not found"
  printf 'adb not found\n' >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  log "no adb device connected"
  printf 'no adb device connected\n' >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

log "appId=$APP_ID label=$LABEL out=$OUT_DIR"

{
  printf 'applicationId=%s\n' "$APP_ID"
  printf 'label=%s\n' "$LABEL"
  printf 'runId=%s\n' "${RUN_ID:-unknown}"
  for prop in \
    ro.product.model \
    ro.soc.model \
    ro.soc.manufacturer \
    ro.hardware \
    ro.build.version.sdk \
    ro.build.fingerprint; do
    printf '%s=' "$prop"
    adb shell getprop "$prop" 2>/dev/null | tr -d '\r'
  done
} >"$OUT_DIR/device_props.txt"

write_missing_or_file files/npu_engine_initialize_dry_run.txt "$OUT_DIR/stage_file.txt"
write_missing_or_file files/npu_engine_initialize_last_stage.txt "$OUT_DIR/last_stage.txt"
write_missing_or_file files/npu_engine_initialize_crash_marker.txt "$OUT_DIR/crash_marker.txt"
write_missing_or_file files/qairt244_native_diag.txt "$OUT_DIR/qairt244_native_diag.txt"
write_missing_or_file files/npu_experiment_probe.txt "$OUT_DIR/probe_snapshot.txt"

adb shell dumpsys package "$APP_ID" >"$OUT_DIR/package_dump.txt" 2>"$OUT_DIR/package_dump.err" || true
NATIVE_DIR="$(grep -m1 'nativeLibraryDir=' "$OUT_DIR/package_dump.txt" 2>/dev/null | sed 's/.*nativeLibraryDir=//' | tr -d '\r')"
if [ -z "$NATIVE_DIR" ]; then
  NATIVE_DIR="$(grep -m1 -oE "/data/app/[^ ]+/${APP_ID}[^ ]*/lib/arm64" "$OUT_DIR/probe_snapshot.txt" "$OUT_DIR/stage_file.txt" 2>/dev/null | head -n 1 | cut -d: -f2-)"
fi

adb logcat -b all -d -t 5000 >"$OUT_DIR/logcat_all_tail.txt" 2>"$OUT_DIR/logcat_all_tail.err" || true
grep -Ei "$APP_ID|$KEYWORDS" "$OUT_DIR/logcat_all_tail.txt" >"$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null || true

adb shell dumpsys dropbox --print >"$OUT_DIR/dropbox_full.txt" 2>"$OUT_DIR/dropbox_full.err" || true
context_extract "$APP_ID" "$OUT_DIR/dropbox_full.txt" "$OUT_DIR/dropbox_app_extract.txt"

TOMBSTONE_PATH="$(adb shell 'ls -t /data/tombstones/tombstone_[0-9][0-9] 2>/dev/null | head -n 1' 2>/dev/null | tr -d '\r')"
if [ -n "$TOMBSTONE_PATH" ]; then
  printf '%s\n' "$TOMBSTONE_PATH" >"$OUT_DIR/tombstone_path.txt"
  adb shell cat "$TOMBSTONE_PATH" >"$OUT_DIR/tombstone_latest.txt" 2>"$OUT_DIR/tombstone_latest.err" || true
else
  printf '<missing>\n' >"$OUT_DIR/tombstone_latest.txt"
fi
adb shell ls -lt /data/tombstones >"$OUT_DIR/tombstone_listing.txt" 2>"$OUT_DIR/tombstone_listing.err" || true

if grep -Fq "$APP_ID" "$OUT_DIR/tombstone_latest.txt" 2>/dev/null; then
  cp "$OUT_DIR/tombstone_latest.txt" "$OUT_DIR/tombstone_app_extract.txt"
  TOMBSTONE_MATCH="latest-tombstone-matches-app"
else
  cp "$OUT_DIR/dropbox_app_extract.txt" "$OUT_DIR/tombstone_app_extract.txt"
  TOMBSTONE_MATCH="latest-tombstone-does-not-match-app; using dropbox app extract"
fi

SOURCE_FOR_PARSE="$OUT_DIR/tombstone_app_extract.txt"
if [ -z "${NATIVE_DIR:-}" ]; then
  NATIVE_DIR="$(
    grep -m1 -oE "/data/app/[^ ]+/${APP_ID}[^ ]*/lib/arm64/liblitertlm_jni\.so" "$SOURCE_FOR_PARSE" 2>/dev/null \
      | sed 's#/liblitertlm_jni\.so$##'
  )"
fi
grep -E "($APP_ID|/lib/arm64/|BuildId:|Build ID:|libLiteRt|liblitertlm|libQnn|libLiteRtRuntimeCApi)" "$SOURCE_FOR_PARSE" >"$OUT_DIR/loaded_libs_extract.txt" 2>/dev/null || true
grep -Ei "cannot locate|library not found|dlopen failed|symbol not found|not found|No usable Dispatch runtime found|insufficient capabilities|Failed to initialize Dispatch API|LiteRtRuntimeCApi" "$SOURCE_FOR_PARSE" "$OUT_DIR/logcat_litert_qnn_extract.txt" >"$OUT_DIR/missing_or_error_strings.txt" 2>/dev/null || true
decode_register_ascii "$SOURCE_FOR_PARSE" "$OUT_DIR/register_ascii_fragments.txt"

APK_PATH="$(pick_local_apk)"
APK_LIB_DIR="$OUT_DIR/apk_libs"
mkdir -p "$APK_LIB_DIR"
{
  printf 'applicationId=%s\n' "$APP_ID"
  printf 'nativeLibraryDir=%s\n' "${NATIVE_DIR:-unknown}"
  printf 'localApk=%s\n' "$APK_PATH"
  printf 'library\tpresent\tsize\tsha256\tbuild_id\tsoname\tneeded\tsource\n'
  for lib in "${LIBS[@]}"; do
    present="false"
    size="-"
    sha="-"
    build="-"
    soname="-"
    needed="-"
    source="missing"
    if [ -n "${NATIVE_DIR:-}" ] && adb shell ls "$NATIVE_DIR/$lib" >/dev/null 2>&1; then
      present="true"
      source="device"
      size="$(adb shell stat -c '%s' "$NATIVE_DIR/$lib" 2>/dev/null | tr -d '\r' || printf '-')"
      sha="$(adb shell sha256sum "$NATIVE_DIR/$lib" 2>/dev/null | awk '{print $1}' | tr -d '\r' || printf '-')"
    fi
    if [ -f "$APK_PATH" ] && unzip -p "$APK_PATH" "lib/arm64-v8a/$lib" >"$APK_LIB_DIR/$lib" 2>/dev/null; then
      [ "$present" = "false" ] && present="true"
      [ "$source" = "missing" ] && source="apk"
      [ "$size" = "-" ] && size="$(wc -c <"$APK_LIB_DIR/$lib" | tr -d ' ')"
      [ "$sha" = "-" ] && sha="$(sha_for "$APK_LIB_DIR/$lib")"
      build="$(build_id_for "$APK_LIB_DIR/$lib")"
      soname="$(soname_for "$APK_LIB_DIR/$lib")"
      needed="$(needed_for "$APK_LIB_DIR/$lib")"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$lib" "$present" "$size" "$sha" "${build:-}" "${soname:-}" "${needed:-}" "$source"
  done
} >"$OUT_DIR/native_lib_build_ids.txt"

{
  printf 'library\tmapped_in_tombstone\tpresent_in_native_library_dir\tbuild_id\n'
  for lib in "${LIBS[@]}"; do
    mapped="false"
    if grep -Fq "/lib/arm64/$lib" "$SOURCE_FOR_PARSE" 2>/dev/null; then
      mapped="true"
    fi
    present="$(awk -F '\t' -v lib="$lib" '$1 == lib { print $2; found=1 } END { if (!found) print "unknown" }' "$OUT_DIR/native_lib_build_ids.txt")"
    build="$(awk -F '\t' -v lib="$lib" '$1 == lib { print $5; found=1 } END { if (!found) print "-" }' "$OUT_DIR/native_lib_build_ids.txt")"
    printf '%s\t%s\t%s\t%s\n' "$lib" "$mapped" "$present" "${build:-}"
  done
} >"$OUT_DIR/loaded_libs_matrix.tsv"

{
  printf '# Loaded native library summary\n\n'
  printf '%s\n\n' "- applicationId: \`$APP_ID\`"
  printf '| Library | Mapped in tombstone | Present in nativeLibraryDir/APK | Build ID |\n'
  printf '| --- | --- | --- | --- |\n'
  tail -n +2 "$OUT_DIR/loaded_libs_matrix.tsv" | while IFS="$(printf '\t')" read -r lib mapped present build; do
    printf '| `%s` | %s | %s | `%s` |\n' "$lib" "$mapped" "$present" "${build:-}"
  done
} >"$OUT_DIR/loaded_libs_summary.md"

{
  printf '# Abort text candidates\n\n'
  printf '## Direct strings\n\n```text\n'
  grep -Eio 'No usable Dispatch runtime found|Failed to create a dispatch delegate kernel|Failed to initialize Dispatch API|Dispatch API has insufficient capabilities|insufficient capabilities|LiteRtDispatchCheckRuntimeCompatibility|libLiteRtRuntimeCApi\.so|LiteRtRuntimeCApi|QNN[^[:cntrl:]]{0,120}|ADSP[^[:cntrl:]]{0,120}|LD_LIBRARY_PATH[^[:cntrl:]]{0,120}' "$SOURCE_FOR_PARSE" "$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null | sort -u || true
  printf '```\n\n## Register ASCII fragments\n\n```text\n'
  cat "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null || true
  printf '```\n\n## Scored candidates\n\n'
  for candidate in \
    "No usable Dispatch runtime found" \
    "Failed to create a dispatch delegate kernel" \
    "Failed to initialize Dispatch API" \
    "insufficient capabilities" \
    "LiteRtDispatchCheckRuntimeCompatibility" \
    "libLiteRtRuntimeCApi" \
    "QNN path / ADSP path"; do
    score=0
    grep -Fqi "$candidate" "$SOURCE_FOR_PARSE" "$OUT_DIR/logcat_litert_qnn_extract.txt" "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null && score=1
    case "$candidate" in
      "No usable Dispatch runtime found")
        if grep -Fq 'ernel: N' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
          grep -Fq 'ch runti' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
          grep -Fq 'me found' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null; then
          score=1
        fi
        ;;
      "Failed to create a dispatch delegate kernel")
        if grep -Fq '] Failed' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
          grep -Fq ' to crea' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
          grep -Fq 'legate k' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null; then
          score=1
        fi
        ;;
      "QNN path / ADSP path")
        grep -Eqi 'QNN|Qnn|HTP|Htp|ADSP|LD_LIBRARY_PATH|cdsprpc' "$SOURCE_FOR_PARSE" "$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null && score=1
        ;;
    esac
    printf -- '- %s: %s\n' "$candidate" "$score"
  done
} >"$OUT_DIR/abort_text_candidates.txt"

signal="$(grep -m1 -E 'signal [0-9]+|signal:' "$SOURCE_FOR_PARSE" | sed 's/^[[:space:]]*//' || true)"
abort_message="$(grep -m1 -E 'Abort message:' "$SOURCE_FOR_PARSE" | sed 's/^[[:space:]]*//' || true)"
process_line="$(grep -m1 -E "Cmdline: $APP_ID|process: $APP_ID|>>> $APP_ID <<<" "$SOURCE_FOR_PARSE" | sed 's/^[[:space:]]*//' || true)"
final_stage="$(cat "$OUT_DIR/last_stage.txt" 2>/dev/null | tr -d '\r' | head -n 1)"
[ -z "$final_stage" ] || [ "$final_stage" = "<missing>" ] && final_stage="$(grep -E 'Engine\.initialize invoking|Engine constructor returned|last stage=' "$OUT_DIR/stage_file.txt" 2>/dev/null | tail -n 1 | tr -d '\r')"
backtrace="$(grep -E '^ *#[0-9]+ pc|Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine|com.google.ai.edge.litertlm.Engine.initialize' "$SOURCE_FOR_PARSE" | head -n 45)"
likely_abort="$(grep -Eio 'No usable Dispatch runtime found|Dispatch API has insufficient capabilities|insufficient capabilities|Failed to initialize Dispatch API|LiteRtRuntimeCApi|cannot locate[^[:cntrl:]]+|library not found|symbol not found|version mismatch' "$SOURCE_FOR_PARSE" "$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null | head -n 5 | paste -sd ';' -)"
if [ -z "$likely_abort" ] &&
  grep -Fq '] Failed' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
  grep -Fq ' to crea' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
  grep -Fq 'legate k' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
  grep -Fq 'ernel: N' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
  grep -Fq 'ch runti' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null &&
  grep -Fq 'me found' "$OUT_DIR/register_ascii_fragments.txt" 2>/dev/null; then
  likely_abort="register-fragments: Failed to create a dispatch delegate kernel: No usable Dispatch runtime found"
fi
process_alive="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
if [ -z "$process_alive" ]; then
  process_alive="not-running"
fi

classification="unknown-native-abort"
confidence="low"
recommended="Preserve artifacts and compare with upstream LiteRT-LM/Gallery source generation."
if printf '%s\n%s\n' "$abort_message" "$likely_abort" | grep -qi 'No usable Dispatch runtime found'; then
  classification="no-usable-dispatch-runtime"
  if printf '%s\n' "$likely_abort" | grep -q '^register-fragments:'; then
    confidence="medium"
  else
    confidence="high"
  fi
  recommended="Report upstream with Gallery stack Build IDs; next compare exact Gallery source/tag or dispatch runtime compatibility."
elif printf '%s\n%s\n' "$abort_message" "$likely_abort" | grep -qi 'insufficient capabilities'; then
  classification="insufficient-capabilities"
  confidence="high"
  recommended="Investigate dispatch API/runtime capability mismatch before any independent build."
elif grep -qi 'LiteRtRuntimeCApi' "$OUT_DIR/missing_or_error_strings.txt"; then
  classification="runtime-c-api-missing"
  confidence="medium"
  recommended="Investigate whether LiteRT runtime C API library is expected by this runtime generation."
elif grep -Eqi 'ADSP|QNN manager|libQnn|Htp|cdsprpc|LD_LIBRARY_PATH|library not found|cannot locate' "$OUT_DIR/missing_or_error_strings.txt"; then
  classification="qnn-path-problem"
  confidence="medium"
  recommended="Investigate QNN/HTP path and ADSP library search behavior in isolated flavor."
elif grep -Eqi 'schema|model|compiled graph|context binary' "$OUT_DIR/missing_or_error_strings.txt"; then
  classification="model-runtime-schema-mismatch"
  confidence="medium"
  recommended="Verify SM8750 model/runtime generation alignment."
elif grep -Eqi 'CheckJNI|GetStringCharsInternal|JniAbort|JNI DETECTED ERROR' "$SOURCE_FOR_PARSE"; then
  classification="unknown-native-abort"
  confidence="medium"
  recommended="Investigate LiteRT-LM Java/Kotlin API and native JNI generation mismatch; latest crash is CheckJNI/string handling, not a confirmed dispatch capability error."
fi

{
  printf '# NPU Engine.initialize crash summary\n\n'
  printf '%s\n' "- applicationId: \`$APP_ID\`"
  printf '%s\n' "- label: \`$LABEL\`"
  printf '%s\n' "- runId: \`${RUN_ID:-unknown}\`"
  printf '%s\n' "- tombstone selection: \`$TOMBSTONE_MATCH\`"
  printf '%s\n' "- device: \`$(grep '^ro.product.model=' "$OUT_DIR/device_props.txt" | cut -d= -f2-)\`"
  printf '%s\n' "- final stage: \`${final_stage:-unknown}\`"
  printf '%s\n' "- process alive after probe: \`$process_alive\`"
  printf '%s\n' "- process line: \`${process_line:-unknown}\`"
  printf '%s\n' "- signal: \`${signal:-unknown}\`"
  printf '%s\n' "- abort message: \`${abort_message:-not-found}\`"
  printf '%s\n' "- likely abort/register/log text: \`${likely_abort:-not-found}\`"
  printf '%s\n' "- classification: \`$classification\`"
  printf '%s\n' "- confidence: \`$confidence\`"
  printf '%s\n\n' "- recommended next action: $recommended"
  printf '## Backtrace Summary\n\n```text\n%s\n```\n\n' "${backtrace:-not-found}"
  printf '## Loaded Libs Summary\n\n```text\n'
  grep -E 'liblitertlm_jni|libLiteRt\.so|libLiteRtDispatch_Qualcomm|libQnnSystem|libQnnHtp|libQnnHtpV79Stub|libQnnHtpV79Skel|libLiteRtRuntimeCApi|libllm_inference_engine_jni' "$OUT_DIR/loaded_libs_extract.txt" | head -n 80 || true
  printf '```\n\n'
  printf '## Loaded Libs Matrix\n\n'
  cat "$OUT_DIR/loaded_libs_summary.md"
  printf '\n\n'
  printf '## Abort Text Candidates\n\n```text\n'
  sed -n '/## Direct strings/,$p' "$OUT_DIR/abort_text_candidates.txt" | head -n 120
  printf '```\n\n'
  printf '## Missing/Error Strings\n\n```text\n'
  head -n 80 "$OUT_DIR/missing_or_error_strings.txt" 2>/dev/null || true
  printf '```\n\n'
  printf '## Native Library Metadata\n\n```text\n'
  cat "$OUT_DIR/native_lib_build_ids.txt"
  printf '```\n'
} >"$OUT_DIR/crash_summary.md"

log "wrote $OUT_DIR"
printf '%s\n' "$OUT_DIR"
