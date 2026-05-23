#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.ninbyo02.lami.customnpu"
APP_PRIVATE_FILE="files/qairt244_diagnostic_runner_summary.txt"
APP_PRIVATE_ABS="/data/user/0/${PACKAGE}/${APP_PRIVATE_FILE}"
DEVICE=""
ARTIFACT=""
RUN_TS="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="artifacts/qairt244_npu_diagnostic_summary_sync/${RUN_TS}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/sync_qairt244_npu_diagnostic_summary_to_app.sh [--artifact PATH] [--device SERIAL]

Copies the latest QAIRT NPU Diagnostic Chat runner summary into the
customBuildExperimentDebug app-private key-value file:
  /data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt

This script does not launch an Activity, initialize LiteRT, run RunDecode, or
generate tokens.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      ARTIFACT="${2:-}"
      shift 2
      ;;
    --device)
      DEVICE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "${OUT_DIR}"

ADB=(adb)
if [[ -n "${DEVICE}" ]]; then
  ADB=(adb -s "${DEVICE}")
fi

adb devices > "${OUT_DIR}/adb_devices.txt"
if [[ -z "${DEVICE}" ]]; then
  DEVICE="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ {print $1; exit}' "${OUT_DIR}/adb_devices.txt")"
  if [[ -n "${DEVICE}" ]]; then
    ADB=(adb -s "${DEVICE}")
  fi
fi

git status --short > "${OUT_DIR}/git_status.txt" || true

if [[ -z "${DEVICE}" ]]; then
  {
    echo "# QAIRT NPU Diagnostic Summary Sync"
    echo
    echo "result=failure"
    echo "reason=no_non_emulator_device"
    echo "app_private_file=${APP_PRIVATE_ABS}"
    echo "npu_generation=not_run"
    echo "activity_launch=not_run"
  } > "${OUT_DIR}/summary.md"
  echo "No non-emulator adb device found. See ${OUT_DIR}/adb_devices.txt" >&2
  exit 1
fi

if [[ -z "${ARTIFACT}" ]]; then
  ARTIFACT="$(
    {
      find artifacts/qairt244_npu_diagnostic_chat_ui_multirun -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true
      find artifacts/qairt244_npu_diagnostic_chat_ui_smoke -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true
    } | sort | tail -1
  )"
fi

if [[ -z "${ARTIFACT}" || ! -d "${ARTIFACT}" ]]; then
  {
    echo "# QAIRT NPU Diagnostic Summary Sync"
    echo
    echo "result=failure"
    echo "reason=artifact_not_found"
    echo "artifact=${ARTIFACT}"
    echo "device=${DEVICE}"
    echo "npu_generation=not_run"
    echo "activity_launch=not_run"
  } > "${OUT_DIR}/summary.md"
  echo "Runner artifact not found: ${ARTIFACT}" >&2
  exit 1
fi

SUMMARY_FILE="${ARTIFACT}/summary.md"
RUN1_FILE="${ARTIFACT}/run1_result.txt"
RUN2_FILE="${ARTIFACT}/run2_result.txt"
SMOKE_FILE="${ARTIFACT}/result.txt"
STALE_FILE="${ARTIFACT}/stale_tombstone_note.md"
KV_FILE="${OUT_DIR}/synced_key_value.txt"
REMOTE_TMP="/data/local/tmp/qairt244_diagnostic_runner_summary_${RUN_TS}.txt"

kv_get() {
  local file="$1"
  local key="$2"
  [[ -f "${file}" ]] || return 0
  sed -n "s/^${key}=//p" "${file}" | tail -1
}

guard_state() {
  local file="$1"
  [[ -f "${file}" ]] || return 0
  grep 'qairt244_diagnostic_chat_guarded_run_v1' "${file}" \
    | tail -1 \
    | sed -n 's/.* state=\([^ ]*\).*/\1/p'
}

state_started_final() {
  local state="$1"
  if [[ "${state}" == "started" ]]; then
    echo "true"
  elif [[ -n "${state}" ]]; then
    echo "false"
  else
    echo "unknown"
  fi
}

summary_value() {
  local key="$1"
  kv_get "${SUMMARY_FILE}" "${key}"
}

memory_after_10s_total() {
  [[ -f "${SUMMARY_FILE}" ]] || return 0
  awk -F'|' '/after 10s/ {gsub(/ /, "", $3); print $3; exit}' "${SUMMARY_FILE}"
}

memory_after_10s_native() {
  [[ -f "${SUMMARY_FILE}" ]] || return 0
  awk -F'|' '/after 10s/ {gsub(/ /, "", $4); print $4; exit}' "${SUMMARY_FILE}"
}

first_non_empty() {
  local value
  for value in "$@"; do
    if [[ -n "${value}" ]]; then
      echo "${value}"
      return 0
    fi
  done
}

if [[ -f "${RUN1_FILE}" ]]; then
  RUN1_SOURCE="${RUN1_FILE}"
else
  RUN1_SOURCE="${SMOKE_FILE}"
fi

if [[ -f "${RUN2_FILE}" ]]; then
  RUN2_SOURCE="${RUN2_FILE}"
else
  RUN2_SOURCE=""
fi

RUN1_GUARD="$(guard_state "${RUN1_SOURCE}")"
RUN2_GUARD="$(guard_state "${RUN2_SOURCE}")"
FINAL_GUARD="$(first_non_empty "$(summary_value run2_last_guard_marker_state)" "${RUN2_GUARD}" "$(summary_value run1_last_guard_marker_state)" "${RUN1_GUARD}")"
STATE_STARTED_FINAL="$(first_non_empty "$(summary_value run2_state_started_final)" "$(state_started_final "${RUN2_GUARD}")" "$(summary_value run1_state_started_final)" "$(state_started_final "${RUN1_GUARD}")")"
TOMBSTONE="$(first_non_empty "$(summary_value run2_tombstone_classification)" "$(summary_value run1_tombstone_classification)" "$(grep -m1 'classification:' "${STALE_FILE}" 2>/dev/null | sed -n 's/.*`\([^`]*\)`.*/\1/p')")"
FRESH_CRASH="$(first_non_empty "$(summary_value fresh_crash)" "false")"
AFTER_10S_TOTAL="$(first_non_empty "$(summary_value after_10s_total_pss_kb)" "$(memory_after_10s_total)")"
AFTER_10S_NATIVE="$(first_non_empty "$(summary_value after_10s_native_heap_pss_kb)" "$(memory_after_10s_native)")"

{
  echo "latest_artifact=${ARTIFACT}"
  echo "artifact=${ARTIFACT}"
  echo "run_count=$(first_non_empty "$(summary_value run_count)" "$(if [[ -n "${RUN2_SOURCE}" ]]; then echo 2; else echo 1; fi)")"
  echo "prompt=$(first_non_empty "$(summary_value prompt)" "$(kv_get "${RUN1_SOURCE}" prompt)" "Hi")"
  echo "max_output_tokens=$(first_non_empty "$(summary_value max_output_tokens)" "$(kv_get "${RUN1_SOURCE}" max_output_tokens)" "3")"
  echo "run1_result=$(first_non_empty "$(summary_value run1_result)" "$(kv_get "${RUN1_SOURCE}" result)" "unknown")"
  echo "run1_output=$(first_non_empty "$(summary_value run1_output)" "$(kv_get "${RUN1_SOURCE}" output)" "unknown")"
  echo "run1_elapsed_ms=$(first_non_empty "$(summary_value run1_elapsed_ms)" "$(kv_get "${RUN1_SOURCE}" elapsed_ms)" "unknown")"
  echo "run1_decode_elapsed_ms=$(first_non_empty "$(summary_value run1_decode_elapsed_ms)" "$(kv_get "${RUN1_SOURCE}" decode_elapsed_ms)" "unknown")"
  echo "run1_last_guard_marker_state=$(first_non_empty "$(summary_value run1_last_guard_marker_state)" "${RUN1_GUARD}" "unknown")"
  echo "run1_state_started_final=$(first_non_empty "$(summary_value run1_state_started_final)" "$(state_started_final "${RUN1_GUARD}")" "unknown")"
  echo "run2_result=$(first_non_empty "$(summary_value run2_result)" "$(kv_get "${RUN2_SOURCE}" result)" "unavailable")"
  echo "run2_output=$(first_non_empty "$(summary_value run2_output)" "$(kv_get "${RUN2_SOURCE}" output)" "unavailable")"
  echo "run2_elapsed_ms=$(first_non_empty "$(summary_value run2_elapsed_ms)" "$(kv_get "${RUN2_SOURCE}" elapsed_ms)" "unavailable")"
  echo "run2_decode_elapsed_ms=$(first_non_empty "$(summary_value run2_decode_elapsed_ms)" "$(kv_get "${RUN2_SOURCE}" decode_elapsed_ms)" "unavailable")"
  echo "run2_last_guard_marker_state=$(first_non_empty "$(summary_value run2_last_guard_marker_state)" "${RUN2_GUARD}" "unavailable")"
  echo "run2_state_started_final=$(first_non_empty "$(summary_value run2_state_started_final)" "$(state_started_final "${RUN2_GUARD}")" "unavailable")"
  echo "final_guard_state=$(first_non_empty "${FINAL_GUARD}" "unknown")"
  echo "state_started_final=$(first_non_empty "${STATE_STARTED_FINAL}" "unknown")"
  echo "after_10s_total_pss_kb=$(first_non_empty "${AFTER_10S_TOTAL}" "unknown")"
  echo "after_10s_native_heap_kb=$(first_non_empty "${AFTER_10S_NATIVE}" "unknown")"
  echo "after_10s_native_heap_pss_kb=$(first_non_empty "${AFTER_10S_NATIVE}" "unknown")"
  echo "tombstone_classification=$(first_non_empty "${TOMBSTONE}" "unknown")"
  echo "tombstone=$(first_non_empty "${TOMBSTONE}" "unknown")"
  echo "fresh_crash=${FRESH_CRASH}"
  echo "normal_chatscreen_npu_route=disabled"
  echo "selected_path_npu=disabled"
  echo "high_level_generateResponse=false"
  echo "streaming=false"
  echo "npu_generation=not_run"
  echo "engine_initialize=not_run"
  echo "run_decode=not_run"
  echo "activity_launch=not_run"
} > "${KV_FILE}"

"${ADB[@]}" shell cmd package dump "${PACKAGE}" 2>&1 \
  | grep -A30 -B5 -Ei 'NpuDiagnosticChatActivity|uses-native-library|libcdsprpc' \
  | sed 's/[[:space:]]\+$//' \
  > "${OUT_DIR}/package_dump_extract.txt" || true
"${ADB[@]}" push "${KV_FILE}" "${REMOTE_TMP}" >/dev/null
"${ADB[@]}" shell run-as "${PACKAGE}" cp "${REMOTE_TMP}" "${APP_PRIVATE_FILE}"
"${ADB[@]}" shell run-as "${PACKAGE}" cat "${APP_PRIVATE_FILE}" > "${OUT_DIR}/synced_remote_key_value.txt"
"${ADB[@]}" shell rm -f "${REMOTE_TMP}" >/dev/null 2>&1 || true

{
  echo "# QAIRT NPU Diagnostic Summary Sync"
  echo
  echo "result=success"
  echo "device=${DEVICE}"
  echo "source_artifact=${ARTIFACT}"
  echo "app_private_file=${APP_PRIVATE_ABS}"
  echo "synced_key_value=${KV_FILE}"
  echo "npu_generation=not_run"
  echo "engine_initialize=not_run"
  echo "run_decode=not_run"
  echo "activity_launch=not_run"
  echo
  echo "## Synced Keys"
  echo
  sed 's/^/- `/' "${KV_FILE}" | sed 's/$/`/'
} > "${OUT_DIR}/summary.md"

echo "${OUT_DIR}"
