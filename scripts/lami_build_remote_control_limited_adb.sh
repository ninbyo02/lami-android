#!/usr/bin/env bash
set -euo pipefail
set -f

# Limited forced-command controller extension for LAMI Android ADB installs
# and qairt244 custom JNI artifact staging.
# Intended deployment target on PC:
#   /home/lami-build/lami-build-control/remote_control.sh
#
# This template keeps the SSH entrypoint restricted. It allows only explicit,
# narrow operations and never exposes arbitrary shell/git/adb passthrough.

CMD="${SSH_ORIGINAL_COMMAND:-}"
REPO="$HOME/repos/lami-android"
LOG_DIR="$HOME/build-logs"
DEFAULT_FLAVOR="standard"
ALLOWED_HOSTS=("10.5.5.3" "192.168.52.52")
LITERT_CUSTOM_ARTIFACT_ROOT="$REPO/artifacts/litert_custom_build"
LITERT_LM_CHECKOUT="$HOME/project/litert-custom-build/LiteRT-LM"
QAIRT244_ROOT="$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225"

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

validate_artifact_basename() {
  local name="$1"
  [[ "$name" =~ ^[0-9]{8}_[0-9]{6}_[A-Za-z0-9._-]+$ ]] || fail
  [[ "$name" != *..* ]] || fail
  [[ "$name" != */* ]] || fail
  printf '%s\n' "$name"
}

artifact_has_qairt244_symbol() {
  local artifact_dir="$1"
  local lib="$artifact_dir/built_libs/liblitertlm_jni.so"
  [[ -f "$lib" ]] || return 1
  readelf -Ws "$lib" 2>/dev/null | grep -q 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
}

resolve_qairt244_artifact_dir() {
  local requested="${1:-}"
  local candidate
  if [[ -n "$requested" ]]; then
    requested="$(validate_artifact_basename "$requested")"
    candidate="$LITERT_CUSTOM_ARTIFACT_ROOT/$requested"
    [[ -d "$candidate" ]] || fail
    artifact_has_qairt244_symbol "$candidate" || {
      echo "artifact does not contain qairt244 nativeRunEditablePrompt symbol: $candidate" >&2
      exit 65
    }
    printf '%s\n' "$candidate"
    return 0
  fi

  while IFS= read -r candidate; do
    if artifact_has_qairt244_symbol "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(find "$LITERT_CUSTOM_ARTIFACT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -rn | awk '{print $2}')

  echo "no qairt244 custom JNI artifact found under $LITERT_CUSTOM_ARTIFACT_ROOT" >&2
  exit 65
}

run_qairt244_artifacts() {
  cd "$REPO"
  printf 'artifact\tstatus\tliblitertlm_jni_sha256\tbuild_id\n'
  find "$LITERT_CUSTOM_ARTIFACT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null |
    sort -rn |
    awk '{print $2}' |
    while IFS= read -r artifact; do
      local lib="$artifact/built_libs/liblitertlm_jni.so"
      local status="missing-liblitertlm_jni"
      local sha="-"
      local build_id="-"
      if [[ -f "$lib" ]]; then
        sha="$(sha256sum "$lib" | awk '{print $1}')"
        build_id="$(readelf -n "$lib" 2>/dev/null | awk '/Build ID:/ {print $3; exit}')"
        if artifact_has_qairt244_symbol "$artifact"; then
          status="qairt244-symbol-present"
        else
          status="qairt244-symbol-missing"
        fi
      fi
      printf '%s\t%s\t%s\t%s\n' "$(basename "$artifact")" "$status" "$sha" "$build_id"
    done
}

run_stage_qairt244_custom_jni() {
  local requested="${1:-}"
  local artifact_dir
  artifact_dir="$(resolve_qairt244_artifact_dir "$requested")"

  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local log_file="$LOG_DIR/stage-qairt244-custom-jni-${timestamp}.log"

  {
    echo "== LAMI stage qairt244 custom JNI =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "artifact=$artifact_dir"
    cd "$REPO"
    scripts/stage_litert_custom_build_stack_for_experiment.sh "${artifact_dir#$REPO/}"
    readelf -Ws app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so | grep 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
    git status --short -- app/src/customBuildExperimentDebug/jniLibs/arm64-v8a || true
    echo "== STAGE OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_build_qairt244_custom_jni() {
  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$LITERT_CUSTOM_ARTIFACT_ROOT/${timestamp}_qairt244_16token"
  local log_file="$LOG_DIR/build-qairt244-custom-jni-${timestamp}.log"

  {
    echo "== LAMI build qairt244 custom JNI =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "litert_lm_checkout=$LITERT_LM_CHECKOUT"
    echo "qairt_root=$QAIRT244_ROOT"
    cd "$REPO"
    OUT_DIR="$artifact_dir" \
      BAZEL_OUTPUT_BASE="$HOME/project/litert-custom-build/bazel_output_base/build_$timestamp" \
      scripts/build_litert_custom_artifacts.sh \
        "$LITERT_LM_CHECKOUT" \
        --qairt-root "$QAIRT244_ROOT" \
        --label qairt244_16token
    artifact_has_qairt244_symbol "$artifact_dir"
    scripts/stage_litert_custom_build_stack_for_experiment.sh "${artifact_dir#$REPO/}"
    readelf -Ws app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so | grep 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
    echo "== BUILD+STAGE OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
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
  qairt244-artifacts)
    run_qairt244_artifacts
    ;;
  stage-qairt244-custom-jni*)
    # shellcheck disable=SC2206 # SSH_ORIGINAL_COMMAND is intentionally split after validation.
    parts=($CMD)
    [[ "${#parts[@]}" -ge 1 && "${#parts[@]}" -le 2 ]] || fail
    run_stage_qairt244_custom_jni "${parts[1]:-}"
    ;;
  build-qairt244-custom-jni)
    run_build_qairt244_custom_jni
    ;;
  install-future\ *)
    # shellcheck disable=SC2206 # SSH_ORIGINAL_COMMAND is intentionally split after validation.
    parts=($CMD)
    [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail
    run_install_future "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}"
    ;;
  help)
    cat <<'EOF'
allowed ADB/install/qairt244 commands:
  adb-devices
  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment]
EOF
    ;;
  *)
    fail
    ;;
esac
