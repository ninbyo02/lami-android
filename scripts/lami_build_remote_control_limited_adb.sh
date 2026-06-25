#!/usr/bin/env bash
set -euo pipefail
set -f

# Limited forced-command controller extension for LAMI Android ADB installs.
# Intended deployment target on PC:
#   /home/lami-build/lami-build-control/remote_control.sh
#
# This template keeps the SSH entrypoint restricted. It allows only:
#   adb-devices
#   install-future <host> <port>
#   install-future <host> <port> <flavor>
#
# Host is allowlisted to avoid turning Hermes SSH into a generic LAN scanner.

CMD="${SSH_ORIGINAL_COMMAND:-}"
REPO="$HOME/repos/lami-android"
LOG_DIR="$HOME/build-logs"
DEFAULT_FLAVOR="standard"
ALLOWED_HOSTS=("10.5.5.3" "192.168.52.52")

fail() {
  echo "not allowed: ${CMD:-<empty>}" >&2
  exit 64
}

validate_host() {
  local host="$1"
  [[ "$host" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail
  local allowed
  for allowed in "${ALLOWED_HOSTS[@]}"; do
    [[ "$host" == "$allowed" ]] && return 0
  done
  fail
}

validate_port() {
  local port="$1"
  [[ "$port" =~ ^[0-9]{1,5}$ ]] || fail
  (( port >= 1 && port <= 65535 )) || fail
}

validate_flavor() {
  local flavor="${1:-$DEFAULT_FLAVOR}"
  case "$flavor" in
    standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment)
      echo "$flavor"
      ;;
    *)
      fail
      ;;
  esac
}

run_install_future() {
  local host="$1"
  local port="$2"
  local flavor="${3:-$DEFAULT_FLAVOR}"
  validate_host "$host"
  validate_port "$port"
  flavor="$(validate_flavor "$flavor")"

  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local log_file="$LOG_DIR/install-future-${timestamp}-${host}-${port}-${flavor}.log"

  {
    echo "== LAMI install future =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    echo "flavor=${flavor}"
    cd "$REPO"
    adb devices -l || true
    adb connect "${host}:${port}"
    ./update.sh update --host "$host" --port "$port" --flavor "$flavor"
    echo "== INSTALL OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

case "$CMD" in
  adb-devices)
    adb devices -l
    ;;
  install-future\ *)
    # shellcheck disable=SC2206 # SSH_ORIGINAL_COMMAND is intentionally split after validation.
    parts=($CMD)
    [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail
    run_install_future "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}"
    ;;
  help)
    cat <<'EOF'
allowed ADB install commands:
  adb-devices
  install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment]
EOF
    ;;
  *)
    fail
    ;;
esac
