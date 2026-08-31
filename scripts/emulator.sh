#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# emulator.sh
# - AVD 起動 / boot 完了待ち / APK インストール / Gradle テスト実行
# - scripts/emulator.env があれば自動で読み込み（DO NOT COMMIT 運用）
# - EMU_BIN / ADB_BIN が指定されていればそれを優先（PATH 問題を回避）
# ==============================================================================

usage() {
  cat <<'USAGE'
Usage:
  emulator.sh list
  emulator.sh start [AVD_NAME]
  emulator.sh stop  [SERIAL]
  emulator.sh wait  [SERIAL]
  emulator.sh install [SERIAL] <APK_PATH>
  emulator.sh test [SERIAL] [GRADLE_TASK] [-- <extra gradle args...>]
  emulator.sh doctor
  emulator.sh data-size [SERIAL]
  emulator.sh recreate-data [AVD_NAME]
  emulator.sh ensure-data-size [SERIAL] [EXPECTED_MB]

Env (can be set via scripts/emulator.env):
  ANDROID_SDK_ROOT    (default: $HOME/Android/Sdk)
  ANDROID_HOME        (default: ANDROID_SDK_ROOT)

  EMU_BIN             (default: $ANDROID_HOME/emulator/emulator if exists, else "emulator")
  ADB_BIN             (default: $ANDROID_HOME/platform-tools/adb if exists, else "adb")

  AVD_NAME            (default: Medium_Phone_API_36)
  EMU_PORT            (default: 5554)
  EMU_SERIAL          (default: emulator-${EMU_PORT})

  EMU_HEADLESS        (default: 0)  # 1 => -no-window
  EMU_NO_SNAPSHOT     (default: 1)  # 1 => -no-snapshot
  EMU_WIPE_DATA       (default: 0)  # 1 => -wipe-data
  EMU_GPU_MODE        (default: swiftshader_indirect) # auto|host|swiftshader_indirect
  EMU_PARTITION_SIZE_MB (default: unset) # target /data size in MB; use recreate-data + ensure-data-size to validate

  DISABLE_ANIMATIONS  (default: 0)  # 1 => disable system animations after boot

  EMU_DEVICE_TIMEOUT  (default: 120) # seconds to wait-for-device
  EMU_BOOT_TIMEOUT    (default: 600) # seconds to sys.boot_completed==1
  STOP_TIMEOUT        (default: 60)  # seconds to stop

  POLL_INTERVAL       (default: 2)   # seconds
  LOG_DIR             (default: logs) # relative to scripts/

Notes:
  - If you run from repo root, prefer: ./scripts/emulator.sh ...
USAGE
}

log() { echo "[$(date +'%F %T')] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

# ------------------------------------------------------------------------------
# Load scripts/emulator.env automatically (if present)
# ------------------------------------------------------------------------------
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${SCRIPT_DIR}/emulator.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/emulator.env"
  set +a
fi

# ------------------------------------------------------------------------------
# Defaults
# ------------------------------------------------------------------------------
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

AVD_NAME_DEFAULT="${AVD_NAME:-Medium_Phone_API_36}"
EMU_PORT="${EMU_PORT:-5554}"
EMU_SERIAL_DEFAULT="${EMU_SERIAL:-emulator-${EMU_PORT}}"

EMU_HEADLESS="${EMU_HEADLESS:-0}"
EMU_NO_SNAPSHOT="${EMU_NO_SNAPSHOT:-1}"
EMU_WIPE_DATA="${EMU_WIPE_DATA:-0}"
EMU_GPU_MODE="${EMU_GPU_MODE:-swiftshader_indirect}"
EMU_PARTITION_SIZE_MB="${EMU_PARTITION_SIZE_MB:-}"

DISABLE_ANIMATIONS="${DISABLE_ANIMATIONS:-0}"

EMU_DEVICE_TIMEOUT="${EMU_DEVICE_TIMEOUT:-120}"
EMU_BOOT_TIMEOUT="${EMU_BOOT_TIMEOUT:-600}"
STOP_TIMEOUT="${STOP_TIMEOUT:-60}"

POLL_INTERVAL="${POLL_INTERVAL:-2}"
LOG_DIR="${LOG_DIR:-logs}"

# ------------------------------------------------------------------------------
# Resolve binaries (prefer env, then SDK absolute paths, then PATH)
# ------------------------------------------------------------------------------
default_emu_bin="${ANDROID_HOME}/emulator/emulator"
default_adb_bin="${ANDROID_HOME}/platform-tools/adb"

if [[ -n "${EMU_BIN:-}" ]]; then
  EMU_BIN="${EMU_BIN}"
elif [[ -x "$default_emu_bin" ]]; then
  EMU_BIN="$default_emu_bin"
else
  EMU_BIN="emulator"
fi

if [[ -n "${ADB_BIN:-}" ]]; then
  ADB_BIN="${ADB_BIN}"
elif [[ -x "$default_adb_bin" ]]; then
  ADB_BIN="$default_adb_bin"
else
  ADB_BIN="adb"
fi

# Put SDK tools on PATH as a fallback (doesn't override EMU_BIN/ADB_BIN)
PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

need() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

# ------------------------------------------------------------------------------
# Emulator flags built from env (can be overridden by EMU_FLAGS entirely)
# ------------------------------------------------------------------------------
build_emu_flags() {
  if [[ -n "${EMU_FLAGS:-}" ]]; then
    echo "${EMU_FLAGS}"
    return 0
  fi

  local flags="-no-boot-anim -netdelay none -netspeed full"

  if [[ "$EMU_HEADLESS" == "1" ]]; then
    flags+=" -no-window"
  fi
  if [[ "$EMU_NO_SNAPSHOT" == "1" ]]; then
    flags+=" -no-snapshot"
  fi
  if [[ "$EMU_WIPE_DATA" == "1" ]]; then
    flags+=" -wipe-data"
  fi
  if [[ -n "${EMU_GPU_MODE:-}" ]]; then
    flags+=" -gpu ${EMU_GPU_MODE}"
  fi
  if [[ -n "${EMU_PARTITION_SIZE_MB:-}" ]]; then
    flags+=" -partition-size ${EMU_PARTITION_SIZE_MB}"
  fi

  echo "$flags"
}

disable_system_animations() {
  local serial="$1"
  # 反映に失敗しても致命ではない
  "$ADB_BIN" -s "$serial" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  "$ADB_BIN" -s "$serial" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  "$ADB_BIN" -s "$serial" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
}

resolve_avd_dir() {
  local avd="${1:-$AVD_NAME_DEFAULT}"
  local avd_base="$HOME/.android/avd"
  local direct_dir="${avd_base}/${avd}.avd"
  local ini_path="${avd_base}/${avd}.ini"
  local ini_dir=""

  if [[ -d "$direct_dir" ]]; then
    echo "$direct_dir"
    return 0
  fi

  if [[ -f "$ini_path" ]]; then
    ini_dir="$(sed -n 's/^path=//p' "$ini_path" | tr -d '\r' | head -n1)"
    if [[ -n "$ini_dir" && -d "$ini_dir" ]]; then
      echo "$ini_dir"
      return 0
    fi
  fi

  return 1
}

get_data_size_mb() {
  local serial="$1"
  local size_kb
  size_kb="$("$ADB_BIN" -s "$serial" shell df -k /data 2>/dev/null | tr -d '\r' | awk 'END{print $2}')"
  [[ "$size_kb" =~ ^[0-9]+$ ]] || die "failed to parse /data size (KB) for ${serial}"
  echo $((size_kb / 1024))
}

get_data_avail_mb() {
  local serial="$1"
  local avail_kb
  avail_kb="$("$ADB_BIN" -s "$serial" shell df -k /data 2>/dev/null | tr -d '\r' | awk 'END{print $4}')"
  [[ "$avail_kb" =~ ^[0-9]+$ ]] || die "failed to parse /data avail (KB) for ${serial}"
  echo $((avail_kb / 1024))
}

data_size() {
  local serial="${1:-$EMU_SERIAL_DEFAULT}"
  wait_boot "$serial"

  local data_size_mb data_avail_mb
  data_size_mb="$(get_data_size_mb "$serial")"
  data_avail_mb="$(get_data_avail_mb "$serial")"
  echo "DATA_SIZE_MB=${data_size_mb}"
  echo "DATA_AVAIL_MB=${data_avail_mb}"
  "$ADB_BIN" -s "$serial" shell df -h /data 2>/dev/null || true
}

ensure_data_size() {
  local serial="${1:-$EMU_SERIAL_DEFAULT}"
  local expected_mb="${2:-${EMU_PARTITION_SIZE_MB:-}}"
  [[ -n "$expected_mb" ]] || die "expected MB is required (set EMU_PARTITION_SIZE_MB or pass [EXPECTED_MB])"
  [[ "$expected_mb" =~ ^[0-9]+$ ]] || die "expected MB must be numeric: ${expected_mb}"

  wait_boot "$serial"
  local actual_mb threshold_mb
  actual_mb="$(get_data_size_mb "$serial")"
  threshold_mb=$((expected_mb * 9 / 10))

  log "Checking /data size: actual=${actual_mb}MB expected=${expected_mb}MB threshold(90%)=${threshold_mb}MB"
  if (( actual_mb < threshold_mb )); then
    die "Configured -partition-size was not reflected in /data. Consider recreating the AVD or creating a new AVD."
  fi
  log "OK: /data size is within expected range"
}

recreate_data() {
  local avd="${1:-$AVD_NAME_DEFAULT}"
  local serial="$EMU_SERIAL_DEFAULT"
  local avd_dir
  avd_dir="$(resolve_avd_dir "$avd" || true)"
  [[ -n "$avd_dir" ]] || die "AVD directory not found for ${avd}"

  stop_emulator "$serial"

  local targets=("userdata-qemu.img" "cache.img")
  local deleted=()
  local f path
  for f in "${targets[@]}"; do
    path="${avd_dir}/${f}"
    if [[ -e "$path" ]]; then
      log "Will delete: $path (exists)"
      rm -f -- "$path"
      deleted+=("$f")
    else
      log "Will delete: $path (not found)"
    fi
  done
  if [[ "${#deleted[@]}" -gt 0 ]]; then
    log "Deleted files: ${deleted[*]}"
  else
    log "Deleted files: (none)"
  fi

  EMU_WIPE_DATA=1 start_emulator "$avd" "$serial"
  data_size "$serial"
}

doctor() {
  log "SCRIPT_DIR=$SCRIPT_DIR"
  log "ANDROID_HOME=$ANDROID_HOME"
  log "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
  log "EMU_BIN=$EMU_BIN"
  log "ADB_BIN=$ADB_BIN"
  log "AVD_NAME(default)=$AVD_NAME_DEFAULT"
  log "EMU_PORT=$EMU_PORT"
  log "EMU_SERIAL(default)=$EMU_SERIAL_DEFAULT"
  log "EMU_HEADLESS=$EMU_HEADLESS EMU_NO_SNAPSHOT=$EMU_NO_SNAPSHOT EMU_WIPE_DATA=$EMU_WIPE_DATA EMU_GPU_MODE=$EMU_GPU_MODE"
  log "EMU_PARTITION_SIZE_MB=${EMU_PARTITION_SIZE_MB:-unset}"
  if avd_dir="$(resolve_avd_dir "$AVD_NAME_DEFAULT" 2>/dev/null)"; then
    log "AVD_DIR=${avd_dir}"
  fi
  log "DISABLE_ANIMATIONS=$DISABLE_ANIMATIONS"
  log "EMU_DEVICE_TIMEOUT=$EMU_DEVICE_TIMEOUT EMU_BOOT_TIMEOUT=$EMU_BOOT_TIMEOUT STOP_TIMEOUT=$STOP_TIMEOUT POLL_INTERVAL=$POLL_INTERVAL"
  log "LOG_DIR=$LOG_DIR"
  log "Computed EMU_FLAGS=$(build_emu_flags)"

  need "$ADB_BIN"
  need "$EMU_BIN"
  command -v sdkmanager >/dev/null 2>&1 || log "WARN: sdkmanager not found (cmdline-tools not on PATH)"

  log "adb version: $("$ADB_BIN" version | head -n1 || true)"
  log "emulator version: $("$EMU_BIN" -version 2>/dev/null | head -n1 || true)"
  log "Available AVDs:"
  "$EMU_BIN" -list-avds || true
  if [[ -n "${EMU_PARTITION_SIZE_MB:-}" ]]; then
    log "configured partition target MB: ${EMU_PARTITION_SIZE_MB}"
    log "current /data size check: ${SCRIPT_DIR}/emulator.sh data-size [SERIAL]"
  fi
}

list_avds() {
  need "$EMU_BIN"
  "$EMU_BIN" -list-avds
}

adb_wait_device() {
  local serial="$1"
  need "$ADB_BIN"
  "$ADB_BIN" start-server >/dev/null 2>&1 || true

  log "Waiting for device: ${serial} (adb timeout ${EMU_DEVICE_TIMEOUT}s)"
  if command -v timeout >/dev/null 2>&1; then
    timeout "${EMU_DEVICE_TIMEOUT}" "$ADB_BIN" -s "$serial" wait-for-device
  else
    "$ADB_BIN" -s "$serial" wait-for-device
  fi
}

adb_boot_completed() {
  local serial="$1"
  "$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r'
}

adb_unlock() {
  local serial="$1"
  "$ADB_BIN" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
}

wait_boot() {
  local serial="$1"
  adb_wait_device "$serial"

  log "Waiting for boot completion: ${serial} (boot timeout ${EMU_BOOT_TIMEOUT}s)"
  local start_ts now elapsed
  start_ts="$(date +%s)"
  while true; do
    now="$(date +%s)"
    elapsed=$((now - start_ts))
    if [[ "$elapsed" -gt "$EMU_BOOT_TIMEOUT" ]]; then
      die "boot timeout: ${serial} did not finish in ${EMU_BOOT_TIMEOUT}s"
    fi

    local boot
    boot="$(adb_boot_completed "$serial" || true)"
    if [[ "$boot" == "1" ]]; then
      break
    fi
    sleep "$POLL_INTERVAL"
  done

  sleep 2
  adb_unlock "$serial"

  if [[ "$DISABLE_ANIMATIONS" == "1" ]]; then
    log "Disabling system animations: ${serial}"
    disable_system_animations "$serial"
  fi

  log "Boot completed: ${serial}"
}

is_running() {
  local serial="$1"
  "$ADB_BIN" devices | awk 'NR>1 && $2=="device"{print $1}' | grep -Fxq "$serial"
}

start_emulator() {
  local avd="${1:-$AVD_NAME_DEFAULT}"
  local serial="${2:-$EMU_SERIAL_DEFAULT}"

  need "$ADB_BIN"
  need "$EMU_BIN"

  if is_running "$serial"; then
    log "Already running: ${serial}"
    wait_boot "$serial"
    return 0
  fi

  local flags
  flags="$(build_emu_flags)"

  local log_dir_abs="${SCRIPT_DIR}/${LOG_DIR}"
  mkdir -p "$log_dir_abs"
  local emu_log="${log_dir_abs}/emulator-${EMU_PORT}-$(date +%Y%m%d_%H%M%S).log"

  log "Starting emulator: AVD=${avd}, SERIAL=${serial}, PORT=${EMU_PORT}"
  log "Flags: ${flags}"
  log "Log: ${emu_log}"

  # -port で serial を固定化（emulator-5554 など）
  nohup "$EMU_BIN" -avd "$avd" -port "$EMU_PORT" $flags >"$emu_log" 2>&1 & disown || true

  wait_boot "$serial"
}

stop_emulator() {
  local serial="${1:-$EMU_SERIAL_DEFAULT}"
  need "$ADB_BIN"

  if ! is_running "$serial"; then
    log "Not running: ${serial}"
    return 0
  fi

  log "Stopping emulator: ${serial}"
  "$ADB_BIN" -s "$serial" emu kill >/dev/null 2>&1 || true

  local start_ts now elapsed
  start_ts="$(date +%s)"
  while true; do
    now="$(date +%s)"
    elapsed=$((now - start_ts))
    if [[ "$elapsed" -gt "$STOP_TIMEOUT" ]]; then
      die "failed to stop: ${serial} (timeout ${STOP_TIMEOUT}s)"
    fi
    if ! is_running "$serial"; then
      log "Stopped: ${serial}"
      return 0
    fi
    sleep 1
  done
}

install_apk() {
  local serial="${1:-$EMU_SERIAL_DEFAULT}"
  local apk="${2:-}"
  [[ -n "$apk" ]] || die "APK path required"
  [[ -f "$apk" ]] || die "APK not found: $apk"
  need "$ADB_BIN"

  wait_boot "$serial"
  log "Installing APK: ${apk} -> ${serial}"
  "$ADB_BIN" -s "$serial" install -r -d "$apk"
}

run_test() {
  local serial="${1:-$EMU_SERIAL_DEFAULT}"
  local task="${2:-:app:connectedDebugAndroidTest}"
  shift 2 || true

  local extra=()
  if [[ "${1:-}" == "--" ]]; then
    shift
    extra=("$@")
  fi

  wait_boot "$serial"

  # scripts/ から叩かれても repo root の gradlew を使う
  local repo_root
  repo_root="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

  log "Running Gradle task: ${task} on ${serial}"
  ( cd "$repo_root" && ANDROID_SERIAL="$serial" ./gradlew "$task" "${extra[@]}" )
}

cmd="${1:-}"
case "$cmd" in
  -h|--help|help|"")
    usage
    ;;
  doctor)
    doctor
    ;;
  data-size)
    data_size "${2:-$EMU_SERIAL_DEFAULT}"
    ;;
  recreate-data)
    recreate_data "${2:-$AVD_NAME_DEFAULT}"
    ;;
  ensure-data-size)
    ensure_data_size "${2:-$EMU_SERIAL_DEFAULT}" "${3:-${EMU_PARTITION_SIZE_MB:-}}"
    ;;
  list)
    list_avds
    ;;
  start)
    start_emulator "${2:-$AVD_NAME_DEFAULT}" "${3:-$EMU_SERIAL_DEFAULT}"
    ;;
  stop)
    stop_emulator "${2:-$EMU_SERIAL_DEFAULT}"
    ;;
  wait)
    wait_boot "${2:-$EMU_SERIAL_DEFAULT}"
    ;;
  install)
    install_apk "${2:-$EMU_SERIAL_DEFAULT}" "${3:-}"
    ;;
  test)
    run_test "${2:-$EMU_SERIAL_DEFAULT}" "${3:-:app:connectedDebugAndroidTest}" "${@:4}"
    ;;
  *)
    die "unknown command: $cmd (use --help)"
    ;;
esac
