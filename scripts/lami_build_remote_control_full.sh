#!/usr/bin/env bash
set -euo pipefail
set -f

CMD="${SSH_ORIGINAL_COMMAND:-}"
REPO="$HOME/repos/lami-android"
LOG_DIR="$HOME/build-logs"
DEFAULT_FLAVOR="standard"
ALLOWED_HOSTS=("10.5.5.3" "192.168.52.52")

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"

fail() {
  echo "not allowed: ${CMD:-<empty>}" >&2
  exit 64
}

validate_branch() {
  local branch="$1"
  case "$branch" in
    main|future|feat/*|fix/*|codex/*|test/*|chore/*|docs/*|refactor/*|ci/*|build/*)
      [[ "$branch" != *..* && "$branch" != /* && "$branch" != *' '* ]] || fail
      printf '%s\n' "$branch"
      ;;
    *) fail ;;
  esac
}

validate_safe_patch_name() {
  local name="$1"
  [[ "$name" =~ ^[A-Za-z0-9._-]+\.patch$ ]] || fail
  [[ "$name" != *..* && "$name" != */* ]] || fail
  printf '%s\n' "$name"
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

validate_pair_code() {
  local code="$1"
  [[ "$code" =~ ^[0-9]{6}$ ]] || fail
}

validate_flavor() {
  local flavor="${1:-$DEFAULT_FLAVOR}"
  case "$flavor" in
    standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment)
      echo "$flavor" ;;
    *) fail ;;
  esac
}

source "$REPO/scripts/lami_build_qairt244_forced_commands.sh"

print_status() {
  echo "== host =="
  hostname
  id
  echo "== repo =="
  cd "$REPO"
  git status --short --branch
  git log --oneline -1
  echo "== env =="
  echo "JAVA_HOME=${JAVA_HOME:-}"
  echo "ANDROID_HOME=${ANDROID_HOME:-}"
  echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}"
}

run_branch_task() {
  local mode="$1"
  local branch="$2"
  branch="$(validate_branch "$branch")"
  mkdir -p "$LOG_DIR"
  local timestamp log_file task
  timestamp="$(date +%Y%m%d-%H%M%S)"
  if [[ "$mode" == "test" ]]; then
    task=":app:testStandardDebugUnitTest"
    log_file="$LOG_DIR/build-${timestamp}-${branch//\//_}.log"
  else
    task=":app:assembleStandardDebug"
    log_file="$LOG_DIR/build-${timestamp}-${branch//\//_}.log"
  fi
  {
    echo "== LAMI build =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "branch=$branch"
    echo "task=$task"
    echo "repo=$REPO"
    echo "java=${JAVA_HOME:-}"
    echo "android=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    cd "$REPO"
    git fetch origin
    git checkout -B "$branch" "origin/$branch"
    git reset --hard "origin/$branch"
    git clean -fdx
    git status --short --branch
    ./gradlew --no-daemon "$task"
    echo "== artifact candidates =="
    find app/build/outputs -type f \( -name '*.apk' -o -name '*.aab' \) -printf '%p\t%s bytes\n' 2>/dev/null | sort || true
    echo "== BUILD OK =="
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
  local timestamp log_file
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/install-future-${timestamp}-${host}-${port}-${flavor}.log"
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

run_adb_pair() {
  local host="$1" port="$2" code="$3"
  validate_host "$host"
  validate_port "$port"
  validate_pair_code "$code"
  mkdir -p "$LOG_DIR"
  local timestamp log_file
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/adb-pair-${timestamp}-${host}-${port}.log"
  {
    echo "== LAMI adb pair =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_pair_target=${host}:${port}"
    adb pair "${host}:${port}" "$code"
    adb devices -l || true
    echo "== ADB PAIR OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_adb_connect() {
  local host="$1" port="$2"
  validate_host "$host"
  validate_port "$port"
  mkdir -p "$LOG_DIR"
  local timestamp log_file
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/adb-connect-${timestamp}-${host}-${port}.log"
  {
    echo "== LAMI adb connect =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    adb connect "${host}:${port}"
    adb devices -l || true
    echo "== ADB CONNECT OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_logcat() {
  local kind="$1" timestamp log_file
  mkdir -p "$LOG_DIR"
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/${kind}-${timestamp}.log"
  case "$kind" in
    adb-logcat-lami)
      adb logcat -d -v time | grep -E 'LAMI|lami|Qairt|QAIRT|LiteRT|litert|QNN|NPU|UnsatisfiedLinkError|Unsupported model signature' | tail -500 | tee "$log_file" ;;
    adb-logcat-recent)
      adb logcat -d -v time | tail -800 | tee "$log_file" ;;
    adb-npu-props)
      adb shell getprop | grep -Ei 'qcom|qualcomm|qnn|htp|dsp|soc|gpu|vulkan|opencl|ro.product|ro.board|ro.hardware' | tee "$log_file" ;;
    adb-npu-phase8|adb-npu-phase0)
      adb logcat -d -v time | grep -E 'NPU|QNN|HTP|LiteRT|litert|QAIRT|Qairt' | tail -800 | tee "$log_file" ;;
    *) fail ;;
  esac
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

case "$CMD" in
  status)
    print_status ;;
  build-branch\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; run_branch_task build "${parts[1]}" ;;
  test-branch\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; run_branch_task test "${parts[1]}" ;;
  logs)
    [[ -f "$LOG_DIR/latest.log" ]] && cat "$LOG_DIR/latest.log" || true ;;
  list-logs)
    find "$LOG_DIR" -maxdepth 1 -type f -name '*.log' -printf '%TY-%Tm-%Td %TH:%TM %p\n' 2>/dev/null | sort -r | head -50 ;;
  adb-devices)
    adb devices -l ;;
  adb-pair\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 4 ]] || fail; run_adb_pair "${parts[1]}" "${parts[2]}" "${parts[3]}" ;;
  adb-connect\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail; run_adb_connect "${parts[1]}" "${parts[2]}" ;;
  qairt244-artifacts|stage-qairt244-custom-jni*|build-qairt244-custom-jni|qairt244-sdk-status)
    lami_qairt244_dispatch "$CMD" ;;
  adb-logcat-lami|adb-logcat-recent|adb-npu-props|adb-npu-phase8|adb-npu-phase0)
    run_logcat "$CMD" ;;
  patch-put\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; name="$(validate_safe_patch_name "${parts[1]}")"; mkdir -p "$HOME/incoming-patches"; cat > "$HOME/incoming-patches/$name"; echo "$HOME/incoming-patches/$name" ;;
  git-status)
    cd "$REPO"; git status --short --branch ;;
  git-remote)
    cd "$REPO"; git remote -v ;;
  git-diff-stat)
    cd "$REPO"; git diff --stat -- . ':(exclude)local.properties' ;;
  git-diff)
    cd "$REPO"; git diff -- . ':(exclude)local.properties' | sed -n '1,500p' ;;
  git-log)
    cd "$REPO"; git log --oneline -20 ;;
  git-apply-check\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; name="$(validate_safe_patch_name "${parts[1]}")"; cd "$REPO"; git apply --check "$HOME/incoming-patches/$name" ;;
  git-apply\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; name="$(validate_safe_patch_name "${parts[1]}")"; cd "$REPO"; git apply "$HOME/incoming-patches/$name" ;;
  git-commit-npu-fallback)
    cd "$REPO"; git add app/build.gradle.kts gradle.properties scripts/lami_build_remote_control_full.sh scripts/lami_build_remote_control_limited_adb.sh scripts/lami_build_qairt244_forced_commands.sh scripts/build_litert_custom_artifacts.sh scripts/stage_litert_custom_build_stack_for_experiment.sh update.sh; git commit -m "fix: update NPU fallback build control" ;;
  install-future\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail; run_install_future "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}" ;;
  help)
    cat <<'EOF'
allowed commands:
  status
  build-branch <main|future|feat/*|fix/*|codex/*|test/*|chore/*|docs/*|refactor/*|ci/*|build/*>
  test-branch <same branch rules>
  logs
  list-logs
  adb-devices
  adb-pair <10.5.5.3|192.168.52.52> <pair-port> <6-digit-code>
  adb-connect <10.5.5.3|192.168.52.52> <connect-port>
  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  qairt244-sdk-status
  adb-logcat-lami
  adb-logcat-recent
  adb-npu-props
  adb-npu-phase8
  adb-npu-phase0
  patch-put <safe-name.patch>   # reads patch from stdin into ~/incoming-patches
  git-status
  git-remote
  git-diff-stat
  git-diff                    # bounded first 500 lines, excludes local.properties
  git-log
  git-apply-check <safe-name.patch>
  git-apply <safe-name.patch>
  git-commit-npu-fallback     # fixed file allowlist + fixed commit message
  install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment]
EOF
    ;;
  *)
    fail ;;
esac
