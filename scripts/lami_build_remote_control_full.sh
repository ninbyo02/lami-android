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
    standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint)
      case "$flavor" in
        gpunoconstraint|no-constraint) echo "standardGpuNoConstraintProvider" ;;
        *) echo "$flavor" ;;
      esac ;;
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
    ./update.sh here-install --host "$host" --port "$port" --flavor "$flavor"
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

app_id_for_flavor() {
  local flavor
  flavor="$(validate_flavor "${1:-$DEFAULT_FLAVOR}")"
  case "$flavor" in
    standard) echo "io.github.ninbyo02.lami" ;;
    npuExperiment) echo "io.github.ninbyo02.lami.npu" ;;
    galleryStackExperiment) echo "io.github.ninbyo02.lami.gallerynpu" ;;
    galleryAlignedNpuProbe) echo "io.github.ninbyo02.lami.galleryprobe" ;;
    customBuildExperiment) echo "io.github.ninbyo02.lami.customnpu" ;;
    standardGpuMinimalRuntimeCandidate) echo "io.github.ninbyo02.lami.gpustandardminimal" ;;
    standardGpuNoConstraintProvider) echo "io.github.ninbyo02.lami.gpunoconstraint" ;;
    trueEngineNpuProbe) echo "io.github.ninbyo02.lami.trueengineprobe" ;;
    *) fail ;;
  esac
}

run_adb_start_app() {
  local host="$1" port="$2" flavor="${3:-$DEFAULT_FLAVOR}"
  validate_host "$host"
  validate_port "$port"
  flavor="$(validate_flavor "$flavor")"
  local app_id
  app_id="$(app_id_for_flavor "$flavor")"

  mkdir -p "$LOG_DIR"
  local timestamp log_file
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/adb-start-app-${timestamp}-${host}-${port}-${flavor}.log"
  {
    echo "== LAMI adb start app =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    echo "flavor=${flavor}"
    echo "app_id=${app_id}"
    adb connect "${host}:${port}"
    adb -s "${host}:${port}" shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1
    echo "== ADB START APP OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_adb_dump_customnpu_diag() {
  local host="$1" port="$2"
  validate_host "$host"
  validate_port "$port"
  mkdir -p "$LOG_DIR"
  local timestamp log_file package
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/adb-dump-customnpu-diag-${timestamp}-${host}-${port}.log"
  package="io.github.ninbyo02.lami.customnpu"
  {
    echo "== LAMI adb dump customnpu diag =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    echo "package=${package}"
    adb connect "${host}:${port}"
    for file in \
      qairt244_short_multitoken_smoke_result.txt \
      qairt244_native_diag.txt \
      qairt244_chat_screen_model_path_resolution.txt \
      qairt244_diagnostic_runner_summary.txt; do
      echo
      echo "===== files/${file} ====="
      adb -s "${host}:${port}" exec-out run-as "$package" cat "files/${file}" 2>/dev/null || true
    done
    echo
    echo "== ADB DUMP CUSTOMNPU DIAG OK =="
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


safe_command_recipes() {
  cat <<'EOF'
safe command recipes:
  npu-token-limit
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences.kt
      patches/qairt244_litertlm_utf8_128token.patch
    commit: debug: raise NPU native max output tokens limit

  forced-command-control
    files:
      scripts/lami_build_remote_control_full.sh
      scripts/lami_build_remote_control_limited_adb.sh
      scripts/lami_build_qairt244_forced_commands.sh
      scripts/build_litert_custom_artifacts.sh
      scripts/stage_litert_custom_build_stack_for_experiment.sh
      update.sh
    commit: fix: update NPU fallback build control

  chat-ui-local-send
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantTtsText.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest.kt
    commit: fix: improve local chat send feedback
EOF
}

run_git_commit_safe_recipe() {
  local recipe="$1"
  local message allowed_regex
  cd "$REPO"
  case "$recipe" in
    npu-token-limit)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences.kt patches/qairt244_litertlm_utf8_128token.patch
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences\.kt|patches/qairt244_litertlm_utf8_128token\.patch)$'
      message="debug: raise NPU native max output tokens limit"
      ;;
    forced-command-control)
      git add scripts/lami_build_remote_control_full.sh scripts/lami_build_remote_control_limited_adb.sh scripts/lami_build_qairt244_forced_commands.sh scripts/build_litert_custom_artifacts.sh scripts/stage_litert_custom_build_stack_for_experiment.sh update.sh
      allowed_regex='^(scripts/lami_build_remote_control_full\.sh|scripts/lami_build_remote_control_limited_adb\.sh|scripts/lami_build_qairt244_forced_commands\.sh|scripts/build_litert_custom_artifacts\.sh|scripts/stage_litert_custom_build_stack_for_experiment\.sh|update\.sh)$'
      message="fix: update NPU fallback build control"
      ;;
    chat-ui-local-send)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantTtsText.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantTtsText\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest\.kt)$'
      message="fix: improve local chat send feedback"
      ;;
    *) fail ;;
  esac
  if git diff --cached --quiet; then
    echo "no staged changes for safe recipe: $recipe" >&2
    exit 65
  fi
  if git diff --cached --name-only | grep -Ev "$allowed_regex"; then
    echo "safe recipe staged files outside allowlist: $recipe" >&2
    exit 65
  fi
  git commit -m "$message"
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
  adb-start-app\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail; run_adb_start_app "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}" ;;
  adb-dump-customnpu-diag\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail; run_adb_dump_customnpu_diag "${parts[1]}" "${parts[2]}" ;;
  qairt244-artifacts|stage-qairt244-custom-jni*|build-qairt244-custom-jni|qairt244-sdk-status|qairt244-repeat-stability|qairt244-token-limit-probe*|litert-gpu-token-probe*|litert-gpu-benchmark-latest|litert-gpu-benchmark-artifact\ *)
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
    cd "$REPO"; git add app/build.gradle.kts gradle.properties app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuOneTurnConversationEntry.kt app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuOneTurnConversationReceiver.kt app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePersistentProbeRunner.kt app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderCreateCloseDevProbe.kt app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderApi.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderChatScreenStandardDebugBlockedTest.kt patches/qairt244_litertlm_utf8_128token_128input.patch patches/qairt244_litertlm_utf8_128token_persistent_probe.patch scripts/lami_build_remote_control_full.sh scripts/lami_build_remote_control_limited_adb.sh scripts/lami_build_qairt244_forced_commands.sh scripts/build_litert_custom_artifacts.sh scripts/stage_litert_custom_build_stack_for_experiment.sh update.sh; git commit -m "fix: update NPU fallback build control" ;;
  git-commit-npu-token-limit)
    cd "$REPO"
    git add \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences.kt \
      patches/qairt244_litertlm_utf8_128token.patch
    git diff --cached --name-only | grep -Ex \
      'app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences\.kt|patches/qairt244_litertlm_utf8_128token\.patch' >/dev/null || fail
    git commit -m "debug: raise NPU native max output tokens limit" ;;
  safe-command-recipes)
    safe_command_recipes ;;
  git-commit-safe-recipe\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; run_git_commit_safe_recipe "${parts[1]}" ;;
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
  adb-start-app <10.5.5.3|192.168.52.52> <connect-port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint]
  adb-dump-customnpu-diag <10.5.5.3|192.168.52.52> <connect-port>
  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  qairt244-sdk-status
  qairt244-repeat-stability
  litert-gpu-token-probe <16|32|64|128|256|512|1024|2048|4096|8192|16384|32768> [gpu|gallery-chat-parity|gpu-null-modalities|cpu] [auto|generic|qualcomm]
  litert-gpu-benchmark-latest
  litert-gpu-benchmark-artifact <YYYYMMDD_HHMMSS>
  qairt244-token-limit-probe <16|32|128|256|512|1024|2048|4096|8192|16384|32768> [current|e2b|e4b]
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
  install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint]
EOF
    ;;
  *)
    fail ;;
esac
