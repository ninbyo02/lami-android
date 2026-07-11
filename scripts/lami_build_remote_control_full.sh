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

validate_source_path() {
  local rel="$1"
  [[ -n "$rel" ]] || fail
  [[ "$rel" != /* && "$rel" != *..* && "$rel" != *' '* ]] || fail
  [[ "$rel" =~ ^[A-Za-z0-9._/@+-]+$ ]] || fail
  case "$rel" in
    local.properties|*.env|*.jks|*.keystore|*google-services.json|*.p12|*.pem|*.key|*.sqlite|*.db)
      fail ;;
    app/src/*|app/build.gradle.kts|build.gradle.kts|settings.gradle.kts|gradle.properties|gradle/libs.versions.toml|scripts/*|patches/*|README.md|AGENTS.md|CLAUDE.md)
      ;;
    *) fail ;;
  esac
  case "$rel" in
    *.kt|*.kts|*.java|*.xml|*.gradle|*.properties|*.toml|*.md|*.sh|*.patch|*.txt|*.json|*.yaml|*.yml)
      ;;
    *) fail ;;
  esac
  printf '%s\n' "$rel"
}

validate_read_number() {
  local value="$1" default_value="$2" min_value="$3" max_value="$4"
  [[ -n "$value" ]] || value="$default_value"
  [[ "$value" =~ ^[0-9]+$ ]] || fail
  (( value >= min_value && value <= max_value )) || fail
  printf '%s\n' "$value"
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


print_fixed_path_status() {
  local label="$1" path="$2"
  local kind="missing" executable="false" readable="false" writable="false" owner="unavailable" mode="unavailable"
  if [[ -d "$path" ]]; then
    kind="dir"
  elif [[ -f "$path" ]]; then
    kind="file"
  elif [[ -e "$path" ]]; then
    kind="other"
  fi
  [[ -r "$path" ]] && readable="true"
  [[ -w "$path" ]] && writable="true"
  [[ -x "$path" ]] && executable="true"
  if [[ -e "$path" ]]; then
    owner="$(stat -c '%U:%G' "$path" 2>/dev/null || printf unavailable)"
    mode="$(stat -c '%a' "$path" 2>/dev/null || printf unavailable)"
  fi
  printf '%s\tpath=%s\tkind=%s\treadable=%s\twritable=%s\texecutable=%s\towner=%s\tmode=%s\n' \
    "$label" "$path" "$kind" "$readable" "$writable" "$executable" "$owner" "$mode"
}

android_sdk_candidate_roots() {
  printf '%s\n' \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    /opt/android-sdk \
    /opt/Android/Sdk \
    /opt/android-sdk-linux \
    "$HOME/lami-android-sdk" \
    /usr/lib/android-sdk \
    "$HOME/Android/Sdk"
}

run_android_sdk_candidates() {
  echo "== android sdk candidates =="
  local root seen=""
  while IFS= read -r root; do
    [[ -n "$root" ]] || continue
    case ":$seen:" in
      *:"$root":*) continue ;;
    esac
    seen="${seen:+$seen:}$root"
    print_fixed_path_status "sdk_root" "$root"
    print_fixed_path_status "emulator_bin" "$root/emulator/emulator"
    print_fixed_path_status "adb_bin" "$root/platform-tools/adb"
  done < <(android_sdk_candidate_roots)
}

run_emulator_env_status() {
  echo "== emulator repo/env status =="
  print_fixed_path_status "repo_scripts_dir" "$REPO/scripts"
  print_fixed_path_status "emulator_script" "$REPO/scripts/emulator.sh"
  print_fixed_path_status "emulator_env" "$REPO/scripts/emulator.env"
  print_fixed_path_status "lami_build_android_dir" "$HOME/.android"
  print_fixed_path_status "lami_build_avd_dir" "$HOME/.android/avd"
  echo "== command availability =="
  command -v grep >/dev/null 2>&1 && echo "grep=available" || echo "grep=missing"
  command -v rg >/dev/null 2>&1 && echo "rg=available" || echo "rg=missing"
}

resolve_emulator_bin_for_status() {
  local root candidate
  while IFS= read -r root; do
    [[ -n "$root" ]] || continue
    candidate="$root/emulator/emulator"
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(android_sdk_candidate_roots)
  if command -v emulator >/dev/null 2>&1; then
    command -v emulator
    return 0
  fi
  return 1
}

run_emulator_avd_list() {
  echo "== emulator avd list =="
  local emu_bin
  emu_bin="$(resolve_emulator_bin_for_status || true)"
  if [[ -z "$emu_bin" ]]; then
    echo "emulator_bin=missing"
    return 65
  fi
  echo "emulator_bin=$emu_bin"
  "$emu_bin" -list-avds || true
}


resolve_sdkmanager_for_status() {
  local root candidate
  while IFS= read -r root; do
    [[ -n "$root" ]] || continue
    for candidate in \
      "$root/cmdline-tools/latest/bin/sdkmanager" \
      "$root/cmdline-tools/bin/sdkmanager" \
      "$root/tools/bin/sdkmanager"; do
      if [[ -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  done < <(android_sdk_candidate_roots)
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi
  return 1
}

resolve_avdmanager_for_status() {
  local root candidate
  while IFS= read -r root; do
    [[ -n "$root" ]] || continue
    for candidate in \
      "$root/cmdline-tools/latest/bin/avdmanager" \
      "$root/cmdline-tools/bin/avdmanager" \
      "$root/tools/bin/avdmanager"; do
      if [[ -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  done < <(android_sdk_candidate_roots)
  if command -v avdmanager >/dev/null 2>&1; then
    command -v avdmanager
    return 0
  fi
  return 1
}

run_android_sdk_tool_status() {
  echo "== android sdk tool status =="
  local sdkmanager avdmanager
  sdkmanager="$(resolve_sdkmanager_for_status || true)"
  avdmanager="$(resolve_avdmanager_for_status || true)"
  echo "sdkmanager=${sdkmanager:-missing}"
  echo "avdmanager=${avdmanager:-missing}"
  echo "install_sdk_root=$HOME/lami-android-sdk"
  print_fixed_path_status "lami_sdk_root" "$HOME/lami-android-sdk"
  print_fixed_path_status "lami_cmdline_tools_latest" "$HOME/lami-android-sdk/cmdline-tools/latest"
  print_fixed_path_status "lami_emulator_bin" "$HOME/lami-android-sdk/emulator/emulator"
  print_fixed_path_status "lami_adb_bin" "$HOME/lami-android-sdk/platform-tools/adb"
}

run_android_sdk_install_emulator() {
  echo "== android sdk install emulator =="
  local sdk_root sdkmanager
  sdk_root="$HOME/lami-android-sdk"
  mkdir -p "$sdk_root"
  sdkmanager="$(resolve_sdkmanager_for_status || true)"
  if [[ -z "$sdkmanager" ]]; then
    echo "sdkmanager=missing"
    return 65
  fi
  echo "sdkmanager=$sdkmanager"
  echo "sdk_root=$sdk_root"
  yes | "$sdkmanager" --sdk_root="$sdk_root" --install "cmdline-tools;latest" "platform-tools" "emulator"
  print_fixed_path_status "lami_emulator_bin" "$sdk_root/emulator/emulator"
  print_fixed_path_status "lami_adb_bin" "$sdk_root/platform-tools/adb"
}

run_android_sdk_list_system_images() {
  echo "== android sdk system image candidates =="
  local sdk_root sdkmanager
  sdk_root="$HOME/lami-android-sdk"
  sdkmanager="$(resolve_sdkmanager_for_status || true)"
  if [[ -z "$sdkmanager" ]]; then
    echo "sdkmanager=missing"
    return 65
  fi
  echo "sdkmanager=$sdkmanager"
  ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root" "$sdkmanager" --sdk_root="$sdk_root" --list 2>/dev/null | grep -E '^  system-images;android-(35|36|36\.1);.*;(x86_64|arm64-v8a)' | sed -n '1,80p' || true
}


run_android_sdk_install_lami_system_image() {
  echo "== android sdk install LAMI emulator system image =="
  local sdk_root sdkmanager
  sdk_root="$HOME/lami-android-sdk"
  mkdir -p "$sdk_root"
  sdkmanager="$(resolve_sdkmanager_for_status || true)"
  if [[ -z "$sdkmanager" ]]; then
    echo "sdkmanager=missing"
    return 65
  fi
  echo "sdkmanager=$sdkmanager"
  echo "sdk_root=$sdk_root"
  yes | ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root" "$sdkmanager" --sdk_root="$sdk_root" --install \
    "platforms;android-36.1" \
    "system-images;android-36.1;google_apis;x86_64"
  print_fixed_path_status "lami_system_image" "$sdk_root/system-images/android-36.1/google_apis/x86_64"
}

run_emulator_create_lami_avd() {
  echo "== create LAMI emulator AVD =="
  local sdk_root avdmanager avd_name package device
  sdk_root="$HOME/lami-android-sdk"
  avdmanager="$(resolve_avdmanager_for_status || true)"
  avd_name="Medium_Phone_API_36.1"
  package="system-images;android-36.1;google_apis;x86_64"
  device="medium_phone"
  if [[ -z "$avdmanager" ]]; then
    echo "avdmanager=missing"
    return 65
  fi
  echo "avdmanager=$avdmanager"
  echo "sdk_root=$sdk_root"
  echo "avd_name=$avd_name"
  echo "package=$package"
  if [[ -d "$HOME/.android/avd/${avd_name}.avd" ]]; then
    echo "avd_status=already_exists"
    run_emulator_avd_list
    return 0
  fi
  mkdir -p "$HOME/.android/avd"
  ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root" echo no | ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root" "$avdmanager" --verbose create avd \
    --name "$avd_name" \
    --package "$package" \
    --device "$device" \
    --force
  run_emulator_avd_list
}


run_emulator_write_lami_env() {
  echo "== write LAMI emulator env =="
  local env_file
  env_file="$REPO/scripts/emulator.env"
  cd "$REPO"
  cat > "$env_file" <<'EOF'
# Generated by lami-build forced command. Do not commit.
ANDROID_SDK_ROOT=/home/lami-build/lami-android-sdk
ANDROID_HOME=/home/lami-build/lami-android-sdk
EMU_BIN=/home/lami-build/lami-android-sdk/emulator/emulator
ADB_BIN=/home/lami-build/lami-android-sdk/platform-tools/adb
AVD_NAME=Medium_Phone_API_36.1
EMU_PORT=5554
EMU_SERIAL=emulator-5554
EMU_HEADLESS=1
EMU_NO_SNAPSHOT=1
EMU_WIPE_DATA=0
EMU_GPU_MODE=swiftshader_indirect
DISABLE_ANIMATIONS=1
EMU_DEVICE_TIMEOUT=180
EMU_BOOT_TIMEOUT=600
STOP_TIMEOUT=90
POLL_INTERVAL=2
LOG_DIR=logs
EOF
  chmod 600 "$env_file"
  print_fixed_path_status "emulator_env" "$env_file"
}

run_emulator_lami_script() {
  local subcmd="$1"
  cd "$REPO"
  [[ -x "$REPO/scripts/emulator.sh" ]] || fail
  case "$subcmd" in
    doctor|list|start|stop|wait)
      "$REPO/scripts/emulator.sh" "$subcmd" ;;
    *) fail ;;
  esac
}


run_emulator_lami_log() {
  echo "== LAMI emulator latest log =="
  local log_dir latest
  log_dir="$REPO/scripts/logs"
  [[ -d "$log_dir" ]] || { echo "log_dir=missing"; return 0; }
  latest="$(find "$log_dir" -maxdepth 1 -type f -name 'emulator-5554-*.log' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n1 | cut -d' ' -f2-)"
  if [[ -z "$latest" ]]; then
    echo "latest_log=missing"
    return 0
  fi
  [[ "$(realpath -m "$latest")" == "$log_dir"/* ]] || fail
  echo "latest_log=$latest"
  echo "== process candidates =="
  ps -ef | grep -E '[e]mulator|[q]emu-system' || true
  echo "== log tail =="
  tail -220 "$latest" || true
}

run_emulator_install_standard_lami() {
  mkdir -p "$LOG_DIR"
  local timestamp log_file apk serial
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/emulator-install-standard-lami-${timestamp}.log"
  apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
  serial="emulator-5554"
  {
    echo "== LAMI emulator install standard =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "serial=${serial}"
    echo "flavor=standard"
    echo "task=:app:assembleStandardDebug"
    echo "apk=${apk}"
    cd "$REPO"
    git status --short --branch
    "$REPO/scripts/emulator.sh" wait "$serial"
    ./gradlew --no-daemon :app:assembleStandardDebug
    test -f "$apk"
    ls -l "$apk"
    "$REPO/scripts/emulator.sh" install "$serial" "$apk"
    echo "== EMULATOR INSTALL STANDARD OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_emulator_start_standard_lami() {
  mkdir -p "$LOG_DIR"
  local timestamp log_file serial app_id
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/emulator-start-standard-lami-${timestamp}.log"
  serial="emulator-5554"
  app_id="io.github.ninbyo02.lami"
  {
    echo "== LAMI emulator start standard =="
    echo "time=$(date -Is)"
    echo "serial=${serial}"
    echo "app_id=${app_id}"
    cd "$REPO"
    "$REPO/scripts/emulator.sh" wait "$serial"
    "$HOME/lami-android-sdk/platform-tools/adb" -s "$serial" shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1
    sleep 5
    echo "== package pid =="
    "$HOME/lami-android-sdk/platform-tools/adb" -s "$serial" shell pidof "$app_id" || true
    echo "== recent lami crash lines =="
    "$HOME/lami-android-sdk/platform-tools/adb" -s "$serial" logcat -d -v time | grep -Ei 'FATAL EXCEPTION|AndroidRuntime|io.github.ninbyo02.lami|LAMI|lami' | tail -120 || true
    echo "== EMULATOR START STANDARD OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
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

run_install_dirty_current() {
  local host="$1"
  local port="$2"
  local flavor="${3:-$DEFAULT_FLAVOR}"
  validate_host "$host"
  validate_port "$port"
  flavor="$(validate_flavor "$flavor")"
  mkdir -p "$LOG_DIR"
  local timestamp log_file task apk app_id
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/install-dirty-current-${timestamp}-${host}-${port}-${flavor}.log"
  case "$flavor" in
    standard) task=":app:assembleStandardDebug"; apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"; app_id="io.github.ninbyo02.lami" ;;
    customBuildExperiment) task=":app:assembleCustomBuildExperimentDebug"; apk="app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk"; app_id="io.github.ninbyo02.lami.customnpu" ;;
    *) fail ;;
  esac
  {
    echo "== LAMI install dirty current =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    echo "flavor=${flavor}"
    echo "task=${task}"
    echo "apk=${apk}"
    cd "$REPO"
    echo "== git status =="
    git status --short --branch
    echo "== build rerun tasks =="
    ./gradlew --no-daemon "$task" --rerun-tasks
    test -f "$apk"
    echo "== apk built =="
    ls -l "$apk"
    adb devices -l || true
    adb connect "${host}:${port}"
    echo "== install apk =="
    adb -s "${host}:${port}" install -r "$apk"
    echo "== INSTALL DIRTY CURRENT OK =="
  } 2>&1 | tee "$log_file"
  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

run_compile_dirty_standard() {
  mkdir -p "$LOG_DIR"
  local timestamp log_file task
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/compile-dirty-standard-${timestamp}.log"
  task=":app:compileStandardDebugKotlin"
  {
    echo "== LAMI compile dirty standard =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "task=${task}"
    cd "$REPO"
    echo "== git status =="
    git status --short --branch
    echo "== compile =="
    ./gradlew --no-daemon "$task"
    echo "== COMPILE DIRTY STANDARD OK =="
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

run_adb_dump_standardnpu_diag() {
  local host="$1" port="$2"
  validate_host "$host"
  validate_port "$port"
  mkdir -p "$LOG_DIR"
  local timestamp log_file package
  timestamp="$(date +%Y%m%d-%H%M%S)"
  log_file="$LOG_DIR/adb-dump-standardnpu-diag-${timestamp}-${host}-${port}.log"
  package="io.github.ninbyo02.lami"
  {
    echo "== LAMI adb dump standard NPU diag =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "adb_target=${host}:${port}"
    echo "package=${package}"
    adb connect "${host}:${port}"
    echo
    echo "== package/native library dirs =="
    adb -s "${host}:${port}" shell dumpsys package "$package" 2>/dev/null       | grep -E 'versionName|versionCode|pkg=|userId=|codePath=|resourcePath=|legacyNativeLibraryDir|primaryCpuAbi|secondaryCpuAbi|nativeLibraryRootDir|nativeLibraryDir'       | head -80 || true
    echo
    echo "== package pid =="
    adb -s "${host}:${port}" shell pidof "$package" 2>/dev/null || true
    echo
    echo "== app private local models =="
    adb -s "${host}:${port}" exec-out run-as "$package" sh -c 'ls -l files/local_models 2>/dev/null || true' || true
    echo
    echo "== app private diagnostic files =="
    adb -s "${host}:${port}" exec-out run-as "$package" sh -c 'ls -l files 2>/dev/null | grep -E "qairt|npu|diag|standard|native|litert" || true' || true
    for file in \
      qairt244_short_multitoken_smoke_result.txt \
      qairt244_native_diag.txt \
      qairt244_chat_screen_model_path_resolution.txt \
      npu_engine_initialize_dry_run.txt \
      npu_engine_initialize_last_stage.txt \
      dev_only_npu_one_turn_conversation_result.txt; do
      echo
      echo "===== files/${file} ====="
      adb -s "${host}:${port}" exec-out run-as "$package" cat "files/${file}" 2>/dev/null | head -220 || true
    done
    echo
    echo "== recent NPU/LiteRT logcat =="
    adb -s "${host}:${port}" logcat -d -v time 2>/dev/null       | grep -Ei 'UnsatisfiedLinkError|No implementation found|dlopen|cannot locate symbol|NPU|QNN|HTP|LiteRT|litert|QAIRT|Qairt|lami_npu_persistent|adapter_failure|nativeLibraryDir|Unsupported model signature'       | tail -260 || true
    echo
    echo "== ADB DUMP STANDARD NPU DIAG OK =="
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

run_read_source() {
  local rel="$1" offset="${2:-1}" limit="${3:-200}" path size
  rel="$(validate_source_path "$rel")"
  offset="$(validate_read_number "$offset" 1 1 200000)"
  limit="$(validate_read_number "$limit" 200 1 400)"
  cd "$REPO"
  path="$REPO/$rel"
  [[ -f "$path" ]] || fail
  [[ "$(realpath -m "$path")" == "$REPO"/* ]] || fail
  size="$(wc -c < "$path")"
  (( size <= 1500000 )) || fail
  awk -v start="$offset" -v max="$limit" 'NR >= start && NR < start + max { printf "%d|%s\n", NR, $0 }' "$path"
}

run_update_live_controller_from_repo() {
  local source target backup_dir backup tmp timestamp
  cd "$REPO"
  source="$REPO/scripts/lami_build_remote_control_full.sh"
  target="$HOME/lami-build-control/remote_control.sh"
  backup_dir="$HOME/lami-build-control/backups"
  timestamp="$(date +%Y%m%d-%H%M%S)"
  backup="$backup_dir/remote_control.sh.$timestamp"
  tmp="$HOME/lami-build-control/.remote_control.sh.$timestamp.tmp"

  [[ -f "$source" ]] || fail
  [[ -f "$target" ]] || fail
  [[ "$(realpath -m "$source")" == "$REPO/scripts/lami_build_remote_control_full.sh" ]] || fail
  [[ "$(realpath -m "$target")" == "$HOME/lami-build-control/remote_control.sh" ]] || fail
  command -v bash >/dev/null 2>&1 || fail
  command -v install >/dev/null 2>&1 || fail

  echo "source=$source"
  echo "target=$target"
  echo "backup=$backup"
  echo "tmp=$tmp"

  bash -n "$source"
  mkdir -p "$backup_dir"
  cp -a "$target" "$backup"
  install -m 755 "$source" "$tmp"
  bash -n "$tmp"

  install -m 755 "$tmp" "$target"
  if ! bash -n "$target"; then
    echo "live controller syntax check failed; restoring backup" >&2
    install -m 755 "$backup" "$target"
    rm -f "$tmp"
    fail
  fi
  rm -f "$tmp"
  echo "controller updated"
  echo "backup=$backup"
  echo "live_recipe_check_begin"
  SSH_ORIGINAL_COMMAND=safe-command-recipes "$target" | grep -A4 local-model-slot-preservation || true
  echo "live_recipe_check_end"
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
      app/build.gradle.kts
      scripts/emulator.sh
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

  tts-success-order
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenTtsOrderingSourceTest.kt
    commit: fix: queue TTS before resetting success state

  screen-orientation-setting
    files:
      app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/ScreenOrientationModeTest.kt
    commit: feat: add screen orientation setting

  local-model-slot-preservation
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen.kt
    commit: fix: preserve separate local model slots

  npu-gpu-diagnostic-safety
    files:
      app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt
      app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt
    commit: debug: harden NPU and GPU diagnostics

  local-gpu-fast-path
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt
    commit: fix: speed up standard GPU local chat path

  npu-missing-jni-fallback
    files:
      app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
    commit: fix: clarify NPU missing JNI fallback

  npu-stats-card
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt
    commit: fix: show local backend fallback in inference stats

  npu-stats-card-unified
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenStreamingRenderTest.kt
    commit: fix: unify local inference stats card display

  lemonade-unload-line-event
    files:
      app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt
      app/src/test/java/io/github/ninbyo02/lami/viewmodels/OpenAiCompatibleProtocolTest.kt
    commit: feat: notify LINE bridge after Lemonade unload

  lemonade-unload-bg
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt
      app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt
    commit: fix: restore delayed Lemonade unload after resume

  chat-timestamp
    files:
      app/src/main/java/io/github/ninbyo02/lami/db/ChatDatabase.kt
      app/src/main/java/io/github/ninbyo02/lami/db/entity/Message.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatBubble.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
    commit: feat: add chat message timestamps

  dev-diagnostics-settings
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt
    commit: fix: gate chat dev diagnostics behind developer access

  resident-router-npu-diagnostics
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt
    commit: fix: align resident router NPU failure diagnostics

  resident-router-context-estimate
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest.kt
    commit: fix: estimate resident router context from prompt

  npu-jni-soname-separation
    files:
      app/build.gradle.kts
      app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuNativeLinkFailureDiagnostics.kt
      scripts/build_litert_custom_artifacts.sh
      scripts/lami_build_qairt244_forced_commands.sh
      scripts/lami_build_remote_control_full.sh
      scripts/stage_litert_custom_build_stack_for_experiment.sh
    commit: fix: separate NPU JNI library SONAME
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
      git add app/build.gradle.kts scripts/emulator.sh scripts/lami_build_remote_control_full.sh scripts/lami_build_remote_control_limited_adb.sh scripts/lami_build_qairt244_forced_commands.sh scripts/build_litert_custom_artifacts.sh scripts/stage_litert_custom_build_stack_for_experiment.sh update.sh
      allowed_regex='^(app/build.gradle\.kts|scripts/emulator\.sh|scripts/lami_build_remote_control_full\.sh|scripts/lami_build_remote_control_limited_adb\.sh|scripts/lami_build_qairt244_forced_commands\.sh|scripts/build_litert_custom_artifacts\.sh|scripts/stage_litert_custom_build_stack_for_experiment\.sh|update\.sh)$'
      message="fix: update NPU fallback build control"
      ;;
    chat-ui-local-send)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantTtsText.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantTtsText\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest\.kt)$'
      message="fix: improve local chat send feedback"
      ;;
    tts-success-order)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenTtsOrderingSourceTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenTtsOrderingSourceTest\.kt)$'
      message="fix: queue TTS before resetting success state"
      ;;
    screen-orientation-setting)
      git add app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/ScreenOrientationModeTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/MainActivity\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/ScreenOrientationModeTest\.kt)$'
      message="feat: add screen orientation setting"
      ;;
    local-model-slot-preservation)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen\.kt)$'
      message="fix: preserve separate local model slots"
      ;;
    npu-gpu-diagnostic-safety)
      git add app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt
      allowed_regex='^(app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver\.kt|app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke\.kt)$'
      message="debug: harden NPU and GPU diagnostics"
      ;;
    local-gpu-fast-path)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner\.kt)$'
      message="fix: speed up standard GPU local chat path"
      ;;
    npu-missing-jni-fallback)
      git add app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      allowed_regex='^(app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt)$'
      message="fix: clarify NPU missing JNI fallback"
      ;;
    npu-stats-card)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder\.kt)$'
      message="fix: show local backend fallback in inference stats"
      ;;
    npu-stats-card-unified)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenStreamingRenderTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreenStreamingRenderTest\.kt)$'
      message="fix: unify local inference stats card display"
      ;;
    lemonade-unload-line-event)
      git add app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt app/src/test/java/io/github/ninbyo02/lami/viewmodels/OpenAiCompatibleProtocolTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel\.kt|app/src/test/java/io/github/ninbyo02/lami/viewmodels/OpenAiCompatibleProtocolTest\.kt)$'
      message="feat: notify LINE bridge after Lemonade unload"
      ;;
    lemonade-unload-bg)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences\.kt|app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel\.kt)$'
      message="fix: restore delayed Lemonade unload after resume"
      ;;
    chat-timestamp)
      git add app/src/main/java/io/github/ninbyo02/lami/db/ChatDatabase.kt app/src/main/java/io/github/ninbyo02/lami/db/entity/Message.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatBubble.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/db/ChatDatabase\.kt|app/src/main/java/io/github/ninbyo02/lami/db/entity/Message\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatBubble\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt)$'
      message="feat: add chat message timestamps"
      ;;
    dev-diagnostics-settings)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings\.kt)$'
      message="fix: gate chat dev diagnostics behind developer access"
      ;;
    resident-router-npu-diagnostics)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider\.kt)$'
      message="fix: align resident router NPU failure diagnostics"
      ;;
    resident-router-context-estimate)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest\.kt)$'
      message="fix: estimate resident router context from prompt"
      ;;
    npu-quality-repair)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1MapperTest.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1MapperTest\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="fix: repair bounded NPU output artifacts"
      ;;
    npu-jni-soname-separation)
      git add app/build.gradle.kts app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuNativeLinkFailureDiagnostics.kt scripts/build_litert_custom_artifacts.sh scripts/lami_build_qairt244_forced_commands.sh scripts/lami_build_remote_control_full.sh scripts/stage_litert_custom_build_stack_for_experiment.sh
      allowed_regex='^(app/build.gradle\.kts|app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuNativeLinkFailureDiagnostics\.kt|scripts/build_litert_custom_artifacts\.sh|scripts/lami_build_qairt244_forced_commands\.sh|scripts/lami_build_remote_control_full\.sh|scripts/stage_litert_custom_build_stack_for_experiment\.sh)$'
      message="fix: separate NPU JNI library SONAME"
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

run_git_commit_push_safe_recipe() {
  local recipe="$1"
  cd "$REPO"
  [[ "$(git branch --show-current)" == "future" ]] || fail
  run_git_commit_safe_recipe "$recipe"
  git push origin future
}

run_git_push_future_ahead_only() {
  cd "$REPO"
  [[ "$(git branch --show-current)" == "future" ]] || fail
  if ! git diff --quiet -- . ':(exclude)local.properties'; then
    echo "worktree has unstaged changes; refusing push" >&2
    git status --short --branch
    exit 65
  fi
  if ! git diff --cached --quiet -- . ':(exclude)local.properties'; then
    echo "index has staged changes; refusing push" >&2
    git status --short --branch
    exit 65
  fi
  git fetch origin future
  local ahead behind
  ahead="$(git rev-list --count origin/future..HEAD)"
  behind="$(git rev-list --count HEAD..origin/future)"
  echo "branch=future ahead=$ahead behind=$behind"
  [[ "$behind" == "0" ]] || { echo "local branch is behind origin/future; refusing push" >&2; exit 65; }
  if [[ "$ahead" == "0" ]]; then
    echo "nothing to push"
    return 0
  fi
  git push origin future
}

run_npu_gpu_diagnostic_safety_check() {
  cd "$REPO"
  [[ "$(git branch --show-current)" == "future" ]] || fail
  echo "== status =="
  git status --short --branch
  echo
  echo "== compile standard debug =="
  ./gradlew --no-daemon :app:compileStandardDebugKotlin
  echo
  echo "== focused standard tests =="
  ./gradlew --no-daemon :app:testStandardDebugUnitTest     --tests '*LiteRtLmGpuBenchmarkRunSummaryTest*'     --tests '*NormalChatGpuDiagnosticsTest*'
}

run_standard_npu_jni_symbol_check() {
  cd "$REPO"
  local apk expected_symbol expected_npu_soname expected_regular_soname latest_log tmp_dir npu_so regular_so
  apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
  expected_symbol="Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt"
  expected_npu_soname="liblami_qairt244_npu_jni.so"
  expected_regular_soname="liblitertlm_jni.so"
  mkdir -p "$LOG_DIR"
  latest_log="$LOG_DIR/standard-npu-jni-symbol-check-$(date +%Y%m%d-%H%M%S).log"
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  {
    echo "== STANDARD NPU JNI APK/SONAME CHECK =="
    echo "time=$(date -Is)"
    echo "repo=$REPO"
    echo "branch=$(git branch --show-current)"
    git status --short --branch
    command -v unzip >/dev/null 2>&1 || { echo "unzip=missing"; exit 65; }
    command -v readelf >/dev/null 2>&1 || { echo "readelf=missing"; exit 65; }
    [[ -f "$apk" ]] || { echo "missing_apk=$apk"; exit 65; }
    unzip -Z1 "$apk" >"$tmp_dir/apk-entries.txt"
    grep -Fxq "lib/arm64-v8a/$expected_npu_soname" "$tmp_dir/apk-entries.txt" || {
      echo "apk_npu_jni=missing path=lib/arm64-v8a/$expected_npu_soname"; exit 65;
    }
    grep -Fxq "lib/arm64-v8a/$expected_regular_soname" "$tmp_dir/apk-entries.txt" || {
      echo "apk_regular_jni=missing path=lib/arm64-v8a/$expected_regular_soname"; exit 65;
    }
    npu_so="$tmp_dir/$expected_npu_soname"
    regular_so="$tmp_dir/$expected_regular_soname"
    unzip -p "$apk" "lib/arm64-v8a/$expected_npu_soname" >"$npu_so"
    unzip -p "$apk" "lib/arm64-v8a/$expected_regular_soname" >"$regular_so"
    [[ -s "$npu_so" && -s "$regular_so" ]] || { echo "apk_jni_extract=empty"; exit 65; }
    local npu_soname regular_soname npu_sha regular_sha
    npu_soname="$(readelf -d "$npu_so" | sed -n 's/.*(SONAME).*\[\(.*\)\].*/\1/p' | head -1)"
    regular_soname="$(readelf -d "$regular_so" | sed -n 's/.*(SONAME).*\[\(.*\)\].*/\1/p' | head -1)"
    npu_sha="$(sha256sum "$npu_so" | awk '{print $1}')"
    regular_sha="$(sha256sum "$regular_so" | awk '{print $1}')"
    echo "npu_jni_sha256=$npu_sha"
    echo "regular_jni_sha256=$regular_sha"
    echo "npu_jni_soname=${npu_soname:-missing}"
    echo "regular_jni_soname=${regular_soname:-missing}"
    [[ "$npu_soname" == "$expected_npu_soname" ]] || { echo "npu_jni_soname_mismatch=true"; exit 65; }
    [[ "$regular_soname" == "$expected_regular_soname" ]] || { echo "regular_jni_soname_mismatch=true"; exit 65; }
    [[ "$npu_sha" != "$regular_sha" ]] || { echo "npu_regular_jni_identical=true"; exit 65; }
    readelf -Ws "$npu_so" >"$tmp_dir/npu-symbols.txt"
    readelf -Ws "$regular_so" >"$tmp_dir/regular-symbols.txt"
    grep -Fq "$expected_symbol" "$tmp_dir/npu-symbols.txt" || {
      echo "npu_nativeRunEditablePrompt_symbol_present=false"; exit 65;
    }
    if grep -Fq "$expected_symbol" "$tmp_dir/regular-symbols.txt"; then
      echo "regular_jni_contains_npu_symbol=true"; exit 65
    fi
    echo "npu_nativeRunEditablePrompt_symbol_present=true"
    echo "regular_jni_contains_npu_symbol=false"
    echo "== STANDARD NPU JNI APK/SONAME CHECK OK =="
  } 2>&1 | tee "$latest_log"
  ln -sfn "$latest_log" "$LOG_DIR/latest.log"
  rm -rf "$tmp_dir"
  trap - RETURN
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
  android-sdk-candidates)
    run_android_sdk_candidates ;;
  emulator-env-status)
    run_emulator_env_status ;;
  emulator-avd-list)
    run_emulator_avd_list ;;
  android-sdk-tool-status)
    run_android_sdk_tool_status ;;
  android-sdk-install-emulator)
    run_android_sdk_install_emulator ;;
  android-sdk-list-system-images)
    run_android_sdk_list_system_images ;;
  android-sdk-install-lami-system-image)
    run_android_sdk_install_lami_system_image ;;
  emulator-create-lami-avd)
    run_emulator_create_lami_avd ;;
  emulator-write-lami-env)
    run_emulator_write_lami_env ;;
  emulator-doctor-lami)
    run_emulator_lami_script doctor ;;
  emulator-list-lami)
    run_emulator_lami_script list ;;
  emulator-start-lami)
    run_emulator_lami_script start ;;
  emulator-stop-lami)
    run_emulator_lami_script stop ;;
  emulator-wait-lami)
    run_emulator_lami_script wait ;;
  emulator-log-lami)
    run_emulator_lami_log ;;
  emulator-install-standard-lami)
    run_emulator_install_standard_lami ;;
  emulator-start-standard-lami)
    run_emulator_start_standard_lami ;;
  adb-pair\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 4 ]] || fail; run_adb_pair "${parts[1]}" "${parts[2]}" "${parts[3]}" ;;
  adb-connect\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail; run_adb_connect "${parts[1]}" "${parts[2]}" ;;
  adb-start-app\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail; run_adb_start_app "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}" ;;
  adb-dump-customnpu-diag\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail; run_adb_dump_customnpu_diag "${parts[1]}" "${parts[2]}" ;;
  adb-dump-standardnpu-diag\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail; run_adb_dump_standardnpu_diag "${parts[1]}" "${parts[2]}" ;;
  qairt244-artifacts|stage-qairt244-custom-jni*|build-qairt244-custom-jni|setup-qairt244-user-patchelf|qairt244-sdk-status|qairt244-repeat-stability|qairt244-token-limit-probe*|litert-gpu-token-probe*|litert-gpu-benchmark-latest|litert-gpu-benchmark-artifact\ *)
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
  read-source\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 2 && "${#parts[@]}" -le 4 ]] || fail; run_read_source "${parts[1]}" "${parts[2]:-1}" "${parts[3]:-200}" ;;
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
  git-commit-push-safe-recipe\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; run_git_commit_push_safe_recipe "${parts[1]}" ;;
  push-future-ahead-only)
    run_git_push_future_ahead_only ;;
  npu-gpu-diagnostic-safety-check)
    run_npu_gpu_diagnostic_safety_check ;;
  standard-npu-jni-symbol-check)
    run_standard_npu_jni_symbol_check ;;
  update-live-controller-from-repo)
    run_update_live_controller_from_repo ;;
  install-future\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail; run_install_future "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}" ;;
  install-dirty-current\ *)
    parts=($CMD); [[ "${#parts[@]}" -ge 3 && "${#parts[@]}" -le 4 ]] || fail; run_install_dirty_current "${parts[1]}" "${parts[2]}" "${parts[3]:-$DEFAULT_FLAVOR}" ;;
  test-dirty-npu-quality-repair)
    cd "$REPO"
    echo "== NPU QUALITY REPAIR DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests '*NpuStandardRouteS1MapperTest*' ;;
  compile-dirty-standard)
    run_compile_dirty_standard ;;
  help)
    cat <<'EOF'
allowed commands:
  status
  build-branch <main|future|feat/*|fix/*|codex/*|test/*|chore/*|docs/*|refactor/*|ci/*|build/*>
  test-branch <same branch rules>
  logs
  list-logs
  adb-devices
  android-sdk-candidates      # fixed read-only SDK path existence check
  emulator-env-status         # fixed read-only emulator script/env/AVD existence check
  emulator-avd-list           # list AVD names via first fixed emulator binary
  android-sdk-tool-status     # fixed read-only sdkmanager/avdmanager check
  android-sdk-install-emulator # fixed install of emulator package into /home/lami-build/Android/Sdk
  android-sdk-list-system-images # fixed list of Android 35/36 system-image candidates
  android-sdk-install-lami-system-image # fixed install of Android 36.1 Google APIs x86_64 image
  emulator-create-lami-avd  # fixed create Medium_Phone_API_36.1 AVD for lami-build
  emulator-write-lami-env  # fixed write scripts/emulator.env for lami-build SDK/AVD
  emulator-doctor-lami|emulator-list-lami|emulator-start-lami|emulator-stop-lami|emulator-wait-lami|emulator-log-lami
  adb-pair <10.5.5.3|192.168.52.52> <pair-port> <6-digit-code>
  adb-connect <10.5.5.3|192.168.52.52> <connect-port>
  adb-start-app <10.5.5.3|192.168.52.52> <connect-port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint]
  adb-dump-customnpu-diag <10.5.5.3|192.168.52.52> <connect-port>
  adb-dump-standardnpu-diag <10.5.5.3|192.168.52.52> <connect-port>
  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  setup-qairt244-user-patchelf
  qairt244-sdk-status
  qairt244-repeat-stability
  litert-gpu-token-probe <16|32|64|128|256|512|1024|2048|4096|8192|16384|32768> [gpu|gallery-chat-parity|gpu-null-modalities|cpu] [auto|generic|qualcomm] [standard|standardGpuNoConstraintProvider] [engine-only|conversation-only|send-message]
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
  read-source <safe-relative-path> [offset] [limit] # bounded read-only source view
  git-log
  git-apply-check <safe-name.patch>
  git-apply <safe-name.patch>
  git-commit-npu-fallback     # fixed file allowlist + fixed commit message
  safe-command-recipes
  git-commit-safe-recipe <recipe>
  git-commit-push-safe-recipe <recipe>
  push-future-ahead-only      # push clean future branch only when ahead of origin/future
  npu-gpu-diagnostic-safety-check
  standard-npu-jni-symbol-check
  update-live-controller-from-repo
  install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint]
  install-dirty-current <10.5.5.3|192.168.52.52> <port> [standard|customBuildExperiment]
  compile-dirty-standard       # dirty worktree compile only, no reset/install
EOF
    ;;
  *)
    fail ;;
esac
