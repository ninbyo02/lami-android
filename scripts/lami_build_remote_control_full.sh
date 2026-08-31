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
source "$REPO/scripts/debug_token_observer_policy.sh"

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

run_test_dirty_sprite_bitmap_ops() {
  cd "$REPO"
  echo "== SPRITE BITMAP OPS DIRTY TEST =="
  git status --short --branch
  ./gradlew --no-daemon :app:testStandardDebugUnitTest \
    --tests 'io.github.ninbyo02.lami.ui.screens.spriteeditor.SpriteBitmapOpsTest'
  echo "== SPRITE BITMAP OPS DIRTY TEST OK =="
}

run_emulator_open_sprite_editor_lami() {
  local adb serial app_id dump
  adb="$HOME/lami-android-sdk/platform-tools/adb"
  serial="emulator-5554"
  app_id="io.github.ninbyo02.lami"
  dump="/sdcard/window-lami-sprite.xml"
  [[ -x "$adb" ]] || fail
  "$adb" -s "$serial" wait-for-device
  [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || fail

  ui_dump() {
    "$adb" -s "$serial" shell uiautomator dump "$dump" >/dev/null
    "$adb" -s "$serial" exec-out cat "$dump" | tr -d '\r'
  }
  tap_matching_node() {
    local pattern="$1" xml bounds x1 y1 x2 y2
    xml="$(ui_dump)"
    bounds="$(printf '%s' "$xml" | python3 -c '
import re, sys, xml.etree.ElementTree as ET
pattern = re.compile(sys.argv[1])
root = ET.fromstring(sys.stdin.read())
parents = {child: parent for parent in root.iter() for child in parent}
for node in root.iter("node"):
    text, desc = node.get("text", ""), node.get("content-desc", "")
    label = text + "\n" + desc
    if not (pattern.fullmatch(text) or pattern.fullmatch(desc)):
        continue
    target = node
    while target is not None and target.get("clickable") != "true":
        target = parents.get(target)
    if target is None:
        target = node
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", target.get("bounds", ""))
    if match:
        print(" ".join(match.groups()))
        print("tap_node=" + repr(label.replace("\n", " / ")) + " clickable=" + target.get("clickable", "") + " bounds=" + target.get("bounds", ""), file=sys.stderr)
        break
' "$pattern")"
    [[ -n "$bounds" ]] || return 1
    read -r x1 y1 x2 y2 <<<"$bounds"
    "$adb" -s "$serial" shell input tap "$(((x1+x2)/2))" "$(((y1+y2)/2))"
    sleep 2
  }
  tap_first_switch() {
    local xml bounds x1 y1 x2 y2
    xml="$(ui_dump)"
    bounds="$(printf '%s' "$xml" | sed 's/></>\n</g' | grep -E 'class="android.widget.(Switch|CheckBox)"|checkable="true"' | head -1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')"
    [[ -n "$bounds" ]] || return 1
    read -r x1 y1 x2 y2 <<<"$bounds"
    "$adb" -s "$serial" shell input tap "$(((x1+x2)/2))" "$(((y1+y2)/2))"
    sleep 2
  }

  "$adb" -s "$serial" shell am force-stop "$app_id"
  "$adb" -s "$serial" shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 12
  if ui_dump | grep -q 'text="Settings"' && ui_dump | grep -q 'text="Sprite Editor"'; then
    tap_matching_node '^Sprite Editor$' || true
    sleep 3
  fi
  if ! ui_dump | grep -q 'content-desc="Sprite Editor Preview"'; then
    tap_matching_node '設定|Settings' || true
    tap_matching_node 'スプライト設定|Sprite Settings' || true
    if ui_dump | grep -q 'text="Sprite Settings"'; then
      if ui_dump | grep -q 'text="開発メニューを表示"'; then
        "$adb" -s "$serial" shell input tap 950 2196; sleep 2
      fi
      "$adb" -s "$serial" shell input keyevent 4
      sleep 2
    fi
    if ! ui_dump | grep -q 'content-desc="Sprite Editor Preview"'; then
      "$adb" -s "$serial" shell input swipe 540 2100 540 500 500
      sleep 1
      tap_matching_node 'スプライトエディタ|Sprite Editor|エディタを開く' || true
      if ! ui_dump | grep -q 'text="Sprite Editor"'; then
        "$adb" -s "$serial" shell input swipe 540 500 540 2100 500
        sleep 1
        tap_matching_node 'スプライトエディタ|Sprite Editor|エディタを開く' || true
      fi
    fi
  fi
  ui_dump | grep -q 'content-desc="Sprite Editor Preview"' || {
    echo "sprite_editor_navigation=failed"
    ui_dump | sed 's/></>\n</g' | grep -E 'text="[^"]+"|content-desc="[^"]+"' | head -120
    return 65
  }
  sprite_ui_failure() {
    echo "sprite_editor_ui_failure=$1"
    ui_dump | sed 's/></>\n</g' | grep -E 'text="[^"]+"|content-desc="[^"]+"' | head -120
    return 65
  }
  tap_matching_node 'More\.\.\.' || { sprite_ui_failure 'more_menu_trigger_missing'; return 65; }
  tap_matching_node 'Resize\.\.\.' || { sprite_ui_failure 'resize_menu_item_missing'; return 65; }
  tap_matching_node '最大 288×288' || { sprite_ui_failure 'resize_288_option_missing'; return 65; }
  ui_dump | grep -q 'text="Resize"' || { sprite_ui_failure 'resize_dialog_title_missing'; return 65; }
  ui_dump | grep -q 'text="最大 96×96"' || { sprite_ui_failure 'resize_96_option_missing'; return 65; }
  ui_dump | grep -q 'text="最大 288×288"' || { sprite_ui_failure 'resize_288_selected_option_missing'; return 65; }
  echo "sprite_editor_resize_dialog=ok"
  echo "resize_option_96=visible"
  echo "resize_option_288=visible_selected"
}

run_emulator_screenshot_sprite_editor_lami() {
  local adb serial out_dir timestamp png xml remote_png remote_xml size
  adb="$HOME/lami-android-sdk/platform-tools/adb"
  serial="emulator-5554"
  out_dir="$HOME/build-logs/emulator-screenshots"
  timestamp="$(date +%Y%m%d-%H%M%S)"
  png="$out_dir/lami-sprite-resize-$timestamp.png"
  xml="$out_dir/lami-sprite-resize-$timestamp.xml"
  remote_png="/sdcard/lami-sprite-resize.png"
  remote_xml="/sdcard/lami-sprite-resize.xml"
  mkdir -p "$out_dir"
  [[ -x "$adb" ]] || fail
  "$adb" -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
  "$adb" -s "$serial" shell screencap -p "$remote_png"
  "$adb" -s "$serial" pull "$remote_png" "$png" >/dev/null
  "$adb" -s "$serial" pull "$remote_xml" "$xml" >/dev/null
  "$adb" -s "$serial" shell rm -f "$remote_png" "$remote_xml"
  grep -q 'text="Resize"' "$xml" || fail
  grep -q 'text="最大 96×96"' "$xml" || fail
  grep -q 'text="最大 288×288"' "$xml" || fail
  size="$(stat -c '%s' "$png")"
  (( size > 0 && size <= 10485760 )) || fail
  echo "screenshot=$png"
  echo "ui_dump=$xml"
  echo "size_bytes=$size"
  echo "png_base64_begin"
  base64 -w0 "$png"
  echo
  echo "png_base64_end"
  echo "ui_xml_begin"
  sed -n '1,120p' "$xml"
  echo "ui_xml_end"
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

run_git_apply_sealed_patch() {
  local patch_path="$1" expected_patch_sha="$2" candidate_worktree="$3"
  python3 - "$patch_path" "$expected_patch_sha" "$candidate_worktree" <<'PY'
import fcntl
import hashlib
import os
import stat
import subprocess
import sys

patch_path, expected_sha, candidate_worktree = sys.argv[1:]
source_fd = None
sealed_fd = None
try:
    source_fd = os.open(patch_path, os.O_RDONLY | os.O_NOFOLLOW)
    source_stat = os.fstat(source_fd)
    if not stat.S_ISREG(source_stat.st_mode):
        raise ValueError("reviewed patch is not a regular file")
    sealed_fd = os.memfd_create("lami-reviewed-patch", os.MFD_ALLOW_SEALING)
    digest = hashlib.sha256()
    while True:
        chunk = os.read(source_fd, 1024 * 1024)
        if not chunk:
            break
        digest.update(chunk)
        view = memoryview(chunk)
        while view:
            written = os.write(sealed_fd, view)
            if written <= 0:
                raise OSError("short write while sealing reviewed patch")
            view = view[written:]
    actual_sha = digest.hexdigest()
    if actual_sha != expected_sha:
        raise ValueError(f"publication patch sha256 mismatch expected={expected_sha} actual={actual_sha}")
    fcntl.fcntl(
        sealed_fd,
        fcntl.F_ADD_SEALS,
        fcntl.F_SEAL_SEAL | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_WRITE,
    )
    for git_args in (("apply", "--check", "--index", "-"), ("apply", "--index", "-")):
        os.lseek(sealed_fd, 0, os.SEEK_SET)
        with os.fdopen(os.dup(sealed_fd), "rb", closefd=True) as patch_stream:
            subprocess.run(
                ["git", *git_args],
                cwd=candidate_worktree,
                stdin=patch_stream,
                check=True,
                close_fds=True,
            )
    print(actual_sha)
except (OSError, ValueError, subprocess.CalledProcessError) as exc:
    print(f"sealed reviewed patch integrity failure: {exc}", file=sys.stderr)
    raise SystemExit(65)
finally:
    if source_fd is not None:
        os.close(source_fd)
    if sealed_fd is not None:
        os.close(sealed_fd)
PY
}

run_assemble_reviewed_gpu_30400_candidate() (
  # Failure codes: 64=invalid arguments, 65=integrity/path/type violation,
  # 70=build failure, 74=temporary/worktree cleanup failure.
  umask 077
  local expected_base_sha="$1" expected_patch_sha="$2"
  local patch_name="t57-publication-candidate.patch"
  local incoming_dir="$HOME/incoming-patches" patch_path="$HOME/incoming-patches/$patch_name"
  local reviewed_input_dir="$HOME/reviewed-inputs" reviewed_patch_snapshot=""
  local reviewed_patch_fd="" reviewed_patch=""
  local clean_parent="$HOME/lami-clean-worktrees" clean_worktree=""
  local remote_sha actual_patch_sha actual_files expected_files actual_raw expected_raw
  local source_apk artifact_base artifact_task_dir artifact_dir artifact_tmp="" apk apk_real
  local artifact_tmp_real artifact_tmp_stat artifact_tmp_sha
  local candidate_tree_sha candidate_diff_sha source_apk_sha apk_sha
  local standard_test_report standard_unit_tests
  local worktree_cleanup_intent=false command_output=""

  secure_private_dir() {
    local dir="$1" parent
    if ! parent="$(dirname "$dir")"; then return 74; fi
    [[ -d "$parent" && ! -L "$parent" ]] || { echo "unsafe parent directory: $parent" >&2; return 65; }
    if [[ ! -e "$dir" ]]; then
      mkdir -m 0700 -- "$dir" || return 74
    fi
    [[ -d "$dir" && ! -L "$dir" ]] || { echo "unsafe directory component: $dir" >&2; return 65; }
    if ! command_output="$(realpath -e -- "$dir")"; then return 65; fi
    [[ "$command_output" == "$dir" ]] || { echo "non-canonical directory component: $dir" >&2; return 65; }
    local dir_owner current_uid
    if ! dir_owner="$(stat -c %u -- "$dir")"; then return 65; fi
    if ! current_uid="$(id -u)"; then return 74; fi
    [[ "$dir_owner" == "$current_uid" ]] || { echo "directory owner mismatch: $dir" >&2; return 65; }
    chmod 0700 -- "$dir" || return 74
  }

  cleanup_reviewed_candidate() {
    local original_rc=$? cleanup_failed=false worktree_listing="" worktree_registered=false
    local partial_registration_recovery=false
    trap - EXIT
    if [[ -n "$artifact_tmp" ]] && ! rm -f -- "$artifact_tmp"; then cleanup_failed=true; fi
    if [[ -n "$reviewed_patch_fd" ]]; then
      exec {reviewed_patch_fd}<&- || cleanup_failed=true
      reviewed_patch_fd=""
    fi
    if [[ -n "$reviewed_patch_snapshot" ]] && ! rm -f -- "$reviewed_patch_snapshot"; then cleanup_failed=true; fi
    if [[ "$worktree_cleanup_intent" == true && -n "$clean_worktree" ]]; then
      if [[ -e "$clean_worktree" ]]; then
        if ! git -C "$REPO" worktree remove --force "$clean_worktree"; then
          partial_registration_recovery=true
          if ! rm -rf -- "$clean_worktree"; then cleanup_failed=true; fi
        fi
      fi
      if [[ "$partial_registration_recovery" == true ]]; then
        if ! git -C "$REPO" worktree prune --expire now; then cleanup_failed=true; fi
      fi
      if worktree_listing="$(git -C "$REPO" worktree list --porcelain 2>/dev/null)"; then
        if [[ $'\n'"$worktree_listing"$'\n' == *$'\n'"worktree $clean_worktree"$'\n'* ]]; then
          worktree_registered=true
          partial_registration_recovery=true
          if ! git -C "$REPO" worktree remove --force "$clean_worktree"; then
            if [[ -e "$clean_worktree" ]] && ! rm -rf -- "$clean_worktree"; then cleanup_failed=true; fi
          fi
          if ! git -C "$REPO" worktree prune --expire now; then cleanup_failed=true; fi
          if ! worktree_listing="$(git -C "$REPO" worktree list --porcelain 2>/dev/null)"; then
            cleanup_failed=true
          elif [[ $'\n'"$worktree_listing"$'\n' == *$'\n'"worktree $clean_worktree"$'\n'* ]]; then
            cleanup_failed=true
          fi
        fi
      else
        cleanup_failed=true
      fi
      if [[ -e "$clean_worktree" ]] && ! rm -rf -- "$clean_worktree"; then cleanup_failed=true; fi
    fi
    if [[ "$cleanup_failed" == true ]]; then
      echo "cleanup_error=reviewed_candidate_temporary_state_remains" >&2
      exit 74
    fi
    exit "$original_rc"
  }
  trap cleanup_reviewed_candidate EXIT

  [[ "$expected_base_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "invalid origin/future commit" >&2; return 64; }
  [[ "$expected_patch_sha" =~ ^[0-9a-f]{64}$ ]] || { echo "invalid publication patch sha256" >&2; return 64; }
  if ! command_output="$(realpath -e -- "$HOME")"; then return 65; fi
  [[ -d "$HOME" && ! -L "$HOME" && "$command_output" == "$HOME" ]] || { echo "unsafe HOME" >&2; return 65; }
  if ! command_output="$(realpath -e -- "$incoming_dir")"; then return 65; fi
  [[ -d "$incoming_dir" && ! -L "$incoming_dir" && "$command_output" == "$incoming_dir" ]] || { echo "unsafe incoming patch directory" >&2; return 65; }
  [[ -f "$patch_path" && ! -L "$patch_path" ]] || { echo "missing or unsafe reviewed patch: $patch_path" >&2; return 65; }
  local patch_owner current_uid
  if ! patch_owner="$(stat -c %u -- "$patch_path")"; then return 65; fi
  if ! current_uid="$(id -u)"; then return 74; fi
  [[ "$patch_owner" == "$current_uid" ]] || { echo "reviewed patch owner mismatch" >&2; return 65; }

  cd "$REPO" || return 65
  if ! git fetch --no-tags origin future; then return 74; fi
  if ! remote_sha="$(git rev-parse --verify refs/remotes/origin/future^{commit})"; then return 65; fi
  [[ "$remote_sha" == "$expected_base_sha" ]] || {
    echo "origin/future commit mismatch expected=$expected_base_sha actual=$remote_sha" >&2
    return 65
  }

  secure_private_dir "$clean_parent" || return $?
  clean_worktree="$(mktemp -d "$clean_parent/t57-gpu-30400-candidate.XXXXXX")" || return 74
  worktree_cleanup_intent=true
  rmdir "$clean_worktree" || return 74
  if ! git worktree add --detach "$clean_worktree" "$remote_sha"; then return 74; fi
  cd "$clean_worktree" || return 65
  if ! command_output="$(git rev-parse HEAD)"; then return 65; fi
  [[ "$command_output" == "$remote_sha" ]] || { echo "detached worktree commit mismatch" >&2; return 65; }
  if ! command_output="$(git branch --show-current)"; then return 65; fi
  [[ -z "$command_output" ]] || { echo "candidate worktree is not detached" >&2; return 65; }
  if ! command_output="$(git status --porcelain --untracked-files=all)"; then return 65; fi
  [[ -z "$command_output" ]] || { echo "candidate baseline worktree is not clean" >&2; return 65; }
  echo "candidate_baseline_status=clean"

  actual_patch_sha="$(run_git_apply_sealed_patch "$patch_path" "$expected_patch_sha" "$clean_worktree")" || return $?
  [[ "$actual_patch_sha" == "$expected_patch_sha" ]] || {
    echo "sealed patch helper returned unexpected sha expected=$expected_patch_sha actual=$actual_patch_sha" >&2
    return 65
  }
  expected_files="$(cat <<'EOF'
app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt
app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt
app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt
app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt
app/src/test/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkRunSummaryTest.kt
scripts/debug_token_observer_policy.sh
scripts/lami_build_remote_control_full.sh
scripts/tests/debug_token_controller_device_gate_test.sh
scripts/tests/debug_token_observer_policy_test.sh
EOF
)"
  actual_files="$(git diff --cached --name-only | LC_ALL=C sort)" || return 65
  [[ "$actual_files" == "$expected_files" ]] || {
    echo "publication patch file allowlist mismatch" >&2
    printf 'expected:\n%s\nactual:\n%s\n' "$expected_files" "$actual_files" >&2
    return 65
  }
  expected_raw="$(cat <<'EOF' | LC_ALL=C sort
M 100644 100644 app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt
M 100644 100644 app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt
M 100644 100644 app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt
M 100644 100644 app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt
M 100644 100644 app/src/test/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkRunSummaryTest.kt
A 000000 100644 scripts/debug_token_observer_policy.sh
M 100644 100644 scripts/lami_build_remote_control_full.sh
A 000000 100644 scripts/tests/debug_token_controller_device_gate_test.sh
A 000000 100644 scripts/tests/debug_token_observer_policy_test.sh
EOF
)"
  actual_raw="$(git diff --cached --raw --no-abbrev | awk -F '\t' '{split(substr($1,2), m, " "); print m[5], m[1], m[2], $2}' | LC_ALL=C sort)" || return 65
  [[ "$actual_raw" == "$expected_raw" ]] || {
    echo "publication patch change-type/mode allowlist mismatch" >&2
    printf 'expected:\n%s\nactual:\n%s\n' "$expected_raw" "$actual_raw" >&2
    return 65
  }
  git diff --quiet || { echo "unexpected unstaged tracked changes" >&2; return 65; }
  if ! command_output="$(git ls-files --others --exclude-standard)"; then return 65; fi
  [[ -z "$command_output" ]] || { echo "unexpected untracked files" >&2; return 65; }
  candidate_tree_sha="$(git write-tree)" || return 65
  candidate_diff_sha="$(git diff --cached --binary | sha256sum | awk '{print $1}')" || return 65
  [[ "$candidate_diff_sha" == "$expected_patch_sha" ]] || {
    echo "candidate diff sha256 mismatch expected=$expected_patch_sha actual=$candidate_diff_sha" >&2
    return 65
  }

  echo "origin_future_commit=$remote_sha"
  echo "publication_patch_snapshot=sealed_memfd"
  echo "publication_patch_sha256=$actual_patch_sha"
  echo "candidate_tree_sha=$candidate_tree_sha"
  echo "candidate_diff_sha256=$candidate_diff_sha"
  echo "candidate_files_begin"
  printf '%s\n' "$actual_files"
  echo "candidate_files_end"
  if ! ./gradlew --no-daemon :app:testStandardDebugUnitTest --rerun-tasks; then
    echo "build_failure=testStandardDebugUnitTest" >&2
    return 70
  fi
  standard_test_report="app/build/reports/tests/testStandardDebugUnitTest/index.html"
  [[ -f "$standard_test_report" && ! -L "$standard_test_report" ]] || { echo "missing or unsafe Standard unit-test report" >&2; return 65; }
  standard_unit_tests="$(sed -n 's/.*<div class="counter">\([0-9][0-9]*\)<\/div>.*/\1/p' "$standard_test_report" | head -1)"
  [[ "$standard_unit_tests" =~ ^[0-9]+$ ]] || { echo "invalid Standard unit-test count" >&2; return 65; }
  echo "standard_unit_tests=$standard_unit_tests"
  if ! ./gradlew --no-daemon :app:assembleStandardGpuNoConstraintProviderDebug --rerun-tasks; then
    echo "build_failure=assembleStandardGpuNoConstraintProviderDebug" >&2
    return 70
  fi

  source_apk="$clean_worktree/app/build/outputs/apk/standardGpuNoConstraintProvider/debug/app-standardGpuNoConstraintProvider-debug.apk"
  [[ -f "$source_apk" && ! -L "$source_apk" && -s "$source_apk" ]] || { echo "missing or unsafe APK: $source_apk" >&2; return 65; }
  artifact_base="$HOME/lami-build-artifacts"
  artifact_task_dir="$artifact_base/t57-gpu-30400"
  artifact_dir="$artifact_task_dir/$candidate_tree_sha"
  secure_private_dir "$artifact_base" || return $?
  secure_private_dir "$artifact_task_dir" || return $?
  secure_private_dir "$artifact_dir" || return $?
  apk="$artifact_dir/app-standardGpuNoConstraintProvider-debug.apk"
  artifact_tmp="$(mktemp "$artifact_dir/.apk.XXXXXX")" || return 74
  [[ -f "$artifact_tmp" && ! -L "$artifact_tmp" ]] || { echo "unsafe APK temporary file" >&2; return 65; }
  cp --reflink=auto -- "$source_apk" "$artifact_tmp" || return 74
  chmod 0600 -- "$artifact_tmp" || return 74
  if ! source_apk_sha="$(sha256sum "$source_apk" | awk '{print $1}')"; then return 74; fi
  if ! artifact_tmp_real="$(realpath -e -- "$artifact_tmp")"; then return 74; fi
  [[ "$artifact_tmp_real" == "$artifact_tmp" ]] || { echo "APK temporary file escaped reviewed artifact path" >&2; return 65; }
  if ! artifact_tmp_stat="$(stat -c 'apk_size=%s apk_mtime=%y' -- "$artifact_tmp")"; then return 74; fi
  [[ -f "$artifact_tmp" && ! -L "$artifact_tmp" && -s "$artifact_tmp" ]] || { echo "APK temporary file is not a nonempty regular file" >&2; return 65; }
  if ! artifact_tmp_sha="$(sha256sum "$artifact_tmp" | awk '{print $1}')"; then return 74; fi
  [[ "$artifact_tmp_sha" == "$source_apk_sha" ]] || { echo "temporary APK sha256 mismatch" >&2; return 65; }
  mv -fT -- "$artifact_tmp" "$apk" || return 74
  artifact_tmp=""
  apk_real="$apk"
  apk_sha="$artifact_tmp_sha"
  echo "apk=$apk_real"
  echo "$artifact_tmp_stat"
  echo "apk_sha256=$apk_sha"
  echo "device_operations=none"
)


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

  resident-router-runtime-evidence
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1AppHistory.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1ProviderTest.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBackendRuntimeEvidenceTest.kt
      scripts/lami_build_remote_control_full.sh
    commit: fix: use runtime evidence for resident NPU routing

  resident-router-model-identity
    files:
      app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper.kt
      scripts/lami_build_remote_control_full.sh
    commit: fix: propagate resolved NPU model identity

  phase1-npu-stats-unified
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest.kt
      scripts/lami_build_remote_control_full.sh
    commit: fix: unify NPU inference stats with local backends

  ralph3-npu-stats-complete
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilderNpuTest.kt
    commit: fix: complete unified NPU inference stats card

  lami-launcher-icon
    files:
      app/src/main/res/drawable/ic_launcher_background.xml
      app/src/main/res/drawable/ic_launcher_foreground_inset70.xml
      app/src/main/res/drawable/ic_launcher_monochrome.xml
      app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
      app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
      app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
      app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml
      app/src/main/res/mipmap-hdpi/ic_launcher.webp
      app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
      app/src/main/res/mipmap-mdpi/ic_launcher.webp
      app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
      app/src/main/res/mipmap-xhdpi/ic_launcher.webp
      app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
      app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
      app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
      app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp
      app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
      app/src/main/res/values/ic_launcher_background.xml
      scripts/lami_build_remote_control_full.sh
    commit: feat: update LAMI launcher icon

  startup-backend-splash
    files:
      app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequence.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckOverlay.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequenceTest.kt
      scripts/lami_build_remote_control_full.sh
    commit: feat: move backend checks into startup splash

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

  runtime-gate-expectations
    files:
      app/src/test/java/io/github/ninbyo02/lami/StandardGpuNoConstraintProviderGradleConfigTest.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest.kt
    commit: test: align runtime gate expectations

  npu-only-model-selection
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceModelSlotTest.kt
    commit: fix: use NPU-only local model with default backend

  local-model-settings-guidance
    files:
      app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlot.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlotTest.kt
    commit: feat: refine local model settings guidance

  sprite-resize-288
    files:
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOps.kt
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteEditorScreen.kt
      app/src/test/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOpsTest.kt
    commit: feat: add 288 sprite selection resize
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
    resident-router-runtime-evidence)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1AppHistory.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1ProviderTest.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBackendRuntimeEvidenceTest.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1AppHistory\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicy\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1ProviderTest\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBackendRuntimeEvidenceTest\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="fix: use runtime evidence for resident NPU routing"
      ;;
    resident-router-model-identity)
      git add app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="fix: propagate resolved NPU model identity"
      ;;
    phase1-npu-stats-unified)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="fix: unify NPU inference stats with local backends"
      ;;
    ralph3-npu-stats-complete)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilderNpuTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilder\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapper\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteInferenceStatsMapperTest\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceStatsSectionBuilderNpuTest\.kt)$'
      message="fix: complete unified NPU inference stats card"
      ;;
    npu-quality-repair)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1MapperTest.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1MapperTest\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="fix: repair bounded NPU output artifacts"
      ;;
    lami-launcher-icon)
      git add app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground_inset70.xml app/src/main/res/drawable/ic_launcher_monochrome.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml app/src/main/res/mipmap-hdpi/ic_launcher.webp app/src/main/res/mipmap-hdpi/ic_launcher_round.webp app/src/main/res/mipmap-mdpi/ic_launcher.webp app/src/main/res/mipmap-mdpi/ic_launcher_round.webp app/src/main/res/mipmap-xhdpi/ic_launcher.webp app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp app/src/main/res/values/ic_launcher_background.xml scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/res/drawable/ic_launcher_background\.xml|app/src/main/res/drawable/ic_launcher_foreground_inset70\.xml|app/src/main/res/drawable/ic_launcher_monochrome\.xml|app/src/main/res/mipmap-anydpi-v26/ic_launcher\.xml|app/src/main/res/mipmap-anydpi-v26/ic_launcher_round\.xml|app/src/main/res/mipmap-anydpi-v33/ic_launcher\.xml|app/src/main/res/mipmap-anydpi-v33/ic_launcher_round\.xml|app/src/main/res/mipmap-hdpi/ic_launcher\.webp|app/src/main/res/mipmap-hdpi/ic_launcher_round\.webp|app/src/main/res/mipmap-mdpi/ic_launcher\.webp|app/src/main/res/mipmap-mdpi/ic_launcher_round\.webp|app/src/main/res/mipmap-xhdpi/ic_launcher\.webp|app/src/main/res/mipmap-xhdpi/ic_launcher_round\.webp|app/src/main/res/mipmap-xxhdpi/ic_launcher\.webp|app/src/main/res/mipmap-xxhdpi/ic_launcher_round\.webp|app/src/main/res/mipmap-xxxhdpi/ic_launcher\.webp|app/src/main/res/mipmap-xxxhdpi/ic_launcher_round\.webp|app/src/main/res/values/ic_launcher_background\.xml|scripts/lami_build_remote_control_full\.sh)$'
      message="feat: update LAMI launcher icon"
      ;;
    startup-backend-splash)
      git add app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequence.kt app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckOverlay.kt app/src/test/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequenceTest.kt scripts/lami_build_remote_control_full.sh
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/MainActivity\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequence\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckOverlay\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/startup/StartupBackendCheckSequenceTest\.kt|scripts/lami_build_remote_control_full\.sh)$'
      message="feat: move backend checks into startup splash"
      ;;
    npu-jni-soname-separation)
      git add app/build.gradle.kts app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuNativeLinkFailureDiagnostics.kt scripts/build_litert_custom_artifacts.sh scripts/lami_build_qairt244_forced_commands.sh scripts/lami_build_remote_control_full.sh scripts/stage_litert_custom_build_stack_for_experiment.sh
      allowed_regex='^(app/build.gradle\.kts|app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuNativeLinkFailureDiagnostics\.kt|scripts/build_litert_custom_artifacts\.sh|scripts/lami_build_qairt244_forced_commands\.sh|scripts/lami_build_remote_control_full\.sh|scripts/stage_litert_custom_build_stack_for_experiment\.sh)$'
      message="fix: separate NPU JNI library SONAME"
      ;;
    runtime-gate-expectations)
      git add app/src/test/java/io/github/ninbyo02/lami/StandardGpuNoConstraintProviderGradleConfigTest.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest.kt
      allowed_regex='^(app/src/test/java/io/github/ninbyo02/lami/StandardGpuNoConstraintProviderGradleConfigTest\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalInferenceResidencyPolicyTest\.kt)$'
      message="test: align runtime gate expectations"
      ;;
    npu-only-model-selection)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceModelSlotTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceModelSlotTest\.kt)$'
      message="fix: use NPU-only local model with default backend"
      ;;
    local-model-settings-guidance)
      git add app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlot.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlotTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/MainActivity\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalBaseModelScreen\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlot\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/LocalModelSlotTest\.kt)$'
      message="feat: refine local model settings guidance"
      ;;
    sprite-resize-288)
      git add app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOps.kt app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteEditorScreen.kt app/src/test/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOpsTest.kt
      allowed_regex='^(app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOps\.kt|app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteEditorScreen\.kt|app/src/test/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteBitmapOpsTest\.kt)$'
      message="feat: add 288 sprite selection resize"
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

run_read_lami_crash_log() {
  local requested="${1:-latest}"
  local file=""
  local -a candidates=()

  if [[ "$requested" == "latest" ]]; then
    mapfile -t candidates < <(find /tmp -maxdepth 1 -type f ! -xtype l \
      \( -name 'lami-crash-raw-*.log' \
      -o -name 'lami-crash-report-*.log' \
      -o -name 'lami-startup-crash-*.log' \
      -o -name 'lami-dropbox-crash-*.log' \) -print)
    (( ${#candidates[@]} > 0 )) || {
      echo "no allowed LAMI crash logs found" >&2
      exit 66
    }
    file="$(printf '%s\n' "${candidates[@]}" | xargs -r stat -c '%Y %n' | sort -nr | head -1 | cut -d' ' -f2-)"
  else
    [[ "$requested" =~ ^lami-(crash-(raw|report)|startup-crash|dropbox-crash)-[0-9]{8}-[0-9]{6}\.log$ ]] || fail
    file="/tmp/$requested"
  fi

  [[ -f "$file" && ! -L "$file" ]] || {
    echo "allowed LAMI crash log not found or is a symlink: $file" >&2
    exit 66
  }
  local canonical size
  canonical="$(readlink -f -- "$file")"
  [[ "$canonical" == /tmp/lami-*.log ]] || fail
  size="$(stat -c '%s' -- "$canonical")"
  (( size <= 5242880 )) || {
    echo "LAMI crash log exceeds 5 MiB read limit: $size bytes" >&2
    exit 66
  }

  echo "file=$canonical"
  echo "size_bytes=$size"
  echo "lines=$(wc -l < "$canonical")"
  echo "== content (max 2000 lines) =="
  sed -n '1,2000p' "$canonical" \
    | sed -E 's/((token|password|secret|authorization|api[_-]?key)[=:][[:space:]]*)[^[:space:]]+/\1[REDACTED]/Ig'
}

run_debug_token_ui_case() (
  local host="$1" port="$2" case_name="$3"
  local observer_max_seconds=600 observer_deadline_ms adb_bin
  observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"
  adb_bin="$(type -P adb)" || { echo "adb_binary=missing" >&2; return 65; }
  adb() { run_debug_token_ui_case_bounded_adb "$observer_deadline_ms" "$adb_bin" "$@"; }
  sleep() { run_debug_token_observer_bounded "$observer_deadline_ms" command sleep "$@"; }
  validate_host "$host"
  validate_port "$port"
  case "$case_name" in gpu16|gpu32|gpu128|gpu512|gpu1024|gpu2048|gpu4096|gpu8192|gpu16384|gpu32768|gpu65536|gpu131072|gpu262144|gpu524288|gpu1048576|gpu-long-2048|gpu-long-8192|gpu-long-16384|gpu-long-22400|gpu-long-24576|gpu-long-28800|gpu-long-30400|gpu-long-32768|gpu-long-32769|gpu-long-30400-r80|gpu-long-30400-r825|gpu-long-30400-r85|gpu-long-30400-payload-22400|gpu-long-30400-payload-28800|gpu-long-32768-payload-30400-r85|gpu-long-30400-flow-compare|gpu-long-30400-typed-callback-compare|cpu32) ;; *) fail ;; esac
  local serial="${host}:${port}" package="io.github.ninbyo02.lami.gpunoconstraint" benchmark_process
  benchmark_process="$package:gpu_benchmark_probe"
  local main_component="$package/io.github.ninbyo02.lami.MainActivity"
  local debug_component="$package/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity"
  local label
  case "$case_name" in
    gpu16) label="GPU 16" ;; gpu32) label="GPU 32" ;; gpu128) label="GPU 128" ;; gpu512) label="GPU 512" ;; gpu1024) label="GPU 1024" ;; gpu2048) label="GPU 2048" ;; gpu4096) label="GPU 4096" ;; gpu8192) label="GPU 8192" ;; gpu16384) label="GPU 16384" ;; gpu32768) label="GPU 32768" ;; gpu65536) label="GPU 65536" ;; gpu131072) label="GPU 131072" ;; gpu262144) label="GPU 262144" ;; gpu524288) label="GPU 524288" ;; gpu1048576) label="GPU 1048576" ;; gpu-long-2048) label="GPU long context 2048" ;; gpu-long-8192) label="GPU long context 8192" ;; gpu-long-16384) label="GPU long context 16384" ;; gpu-long-22400) label="GPU long context 22400" ;; gpu-long-24576) label="GPU long context 24576" ;; gpu-long-28800) label="GPU long context 28800" ;; gpu-long-30400) label="GPU long context 30400" ;; gpu-long-30400-r80) label="GPU long 30400 ratio 80%" ;; gpu-long-30400-r825) label="GPU long 30400 ratio 82.5%" ;; gpu-long-30400-r85) label="GPU long 30400 ratio 85%" ;; gpu-long-30400-payload-22400) label="GPU 30400 payload-equivalent 22400" ;; gpu-long-30400-payload-28800) label="GPU 30400 payload-equivalent 28800" ;; gpu-long-32768-payload-30400-r85) label="GPU 32768 same bytes as 30400 ratio 85%" ;; gpu-long-30400-flow-compare) label="GPU 30400 Flow comparison" ;; gpu-long-30400-typed-callback-compare) label="GPU 30400 typed callback comparison" ;; gpu-long-32768) label="GPU long context 32768" ;; gpu-long-32769) label="GPU long context 32769 boundary" ;; cpu32) label="CPU 32" ;;
  esac
  cd "$REPO"
  local devices connected_count model state pid_before pid_after benchmark_pid_before benchmark_pid_after pid_query_status benchmark_pid_query_status resumed process_gate started_main=false
  devices="$(adb devices -l)"
  printf '%s\n' "$devices"
  connected_count="$(printf '%s\n' "$devices" | awk '$2=="device"{n++} END{print n+0}')"
  [[ "$connected_count" == "1" ]] || { echo "device_gate=blocked connected_device_count=$connected_count"; return 65; }
  state="$(adb -s "$serial" get-state 2>/dev/null || true)"
  model="$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  [[ "$state" == "device" && "$model" == "NX733J" ]] || { echo "device_gate=blocked serial=$serial state=$state model=$model"; return 65; }
  # Fixed debug-only surface: reset Activity/Compose scroll state before every allowlisted case.
  adb -s "$serial" shell am force-stop "$package"
  pid_before="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n' || true)"
  resumed="$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
  if [[ -z "$pid_before" || ( "$resumed" != *"$main_component"* && "$resumed" != *"$debug_component"* ) ]]; then
    adb -s "$serial" shell am start -W -n "$debug_component" >/dev/null
    started_main=true
    for _ in $(seq 1 30); do
      pid_before="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n' || true)"
      resumed="$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
      [[ -n "$pid_before" && "$resumed" == *"$debug_component"* ]] && break
      sleep 1
    done
  fi
  [[ -n "$pid_before" && ( "$resumed" == *"$main_component"* || "$resumed" == *"$debug_component"* ) ]] || {
    echo "foreground_gate=blocked pid=${pid_before:-none} resumed=$resumed started_main=$started_main"; return 65;
  }
  echo "foreground_gate=ok serial=$serial model=$model pid=$pid_before resumed=$resumed started_main=$started_main"

  local baseline out_root pre_exit post_exit remote_xml local_xml bounds x1 y1 x2 y2 tap_x tap_y timestamp=""
  baseline="$(date +%Y%m%d_%H%M%S)"
  out_root="$REPO/artifacts/debug_token_ui/$baseline-$case_name"
  mkdir -p "$out_root"
  pre_exit="$out_root/exit_info_before.txt"
  post_exit="$out_root/exit_info_after.txt"
  adb -s "$serial" shell dumpsys activity exit-info "$package" >"$pre_exit" 2>&1 || true
  printf 'debug_activity_start=reused_foreground_launch\n' >"$out_root/open_debug_activity.txt"
  for _ in $(seq 1 20); do
    resumed="$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
    [[ "$resumed" == *"DebugTokenBenchmarkActivity"* ]] && break
    sleep 1
  done
  [[ "$resumed" == *"DebugTokenBenchmarkActivity"* ]] || { echo "debug_activity_focus=blocked resumed=$resumed"; return 65; }
  remote_xml="/data/local/tmp/lami_debug_token_ui.xml"
  local_xml="$out_root/ui_before_tap.xml"
  adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
  adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
  adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
  bounds="$(python3 - "$local_xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == sys.argv[2]:
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            print(' '.join(m.groups())); break
PY
)"
  for _ in $(seq 1 6); do
    [[ -n "$bounds" ]] && break
    adb -s "$serial" shell input swipe 540 2100 540 350 350
    sleep 1
    adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
    adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
    adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
    bounds="$(python3 - "$local_xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == sys.argv[2]:
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            print(' '.join(m.groups())); break
PY
)"
  done
  read -r x1 y1 x2 y2 <<<"$bounds"
  [[ -n "${x1:-}" && -n "${y1:-}" && -n "${x2:-}" && -n "${y2:-}" ]] || {
    echo "fixed_ui_verb=blocked label=$label"
    echo "visible_ui_labels_begin"
    python3 - "$local_xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    labels = []
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        if text: labels.append('text=' + repr(text))
        if desc: labels.append('content_desc=' + repr(desc))
    print('\n'.join(labels[:120]))
except Exception as e:
    print('ui_dump_parse_error=' + type(e).__name__)
PY
    echo "visible_ui_labels_end"
    return 65
  }
  tap_x=$(( (x1 + x2) / 2 )); tap_y=$(( (y1 + y2) / 2 ))
  previous_timestamp="$(adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null | awk '/^timestamp=/{ts=$0;sub(/^timestamp=/,"",ts)} /^stage=ui_foreground_start$/{print ts}' | tail -1)"
  baseline="$previous_timestamp"
  adb -s "$serial" shell input tap "$tap_x" "$tap_y"
  echo "fixed_ui_verb=tap label=$label x=$tap_x y=$tap_y"
  for _ in $(seq 1 40); do
    timestamp="$(adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null | awk '/^timestamp=/{ts=$0;sub(/^timestamp=/,"",ts)} /^stage=ui_foreground_start$/{print ts}' | tail -1)"
    [[ "$timestamp" =~ ^[0-9]{8}_[0-9]{6}$ && "$timestamp" > "$baseline" ]] && break
    sleep 1
  done
  [[ "$timestamp" > "$baseline" ]] || timestamp=""
  [[ "$timestamp" =~ ^[0-9]{8}_[0-9]{6}$ ]] || { echo "ui_marker=missing"; return 65; }
  echo "ui_marker=observed timestamp=$timestamp transport=foreground_ui_internal"
  set +e
  benchmark_pid_before="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"
  benchmark_pid_query_status=$?
  set -e
  (( benchmark_pid_query_status <= 1 )) || { echo "benchmark_process_gate=query_failed status=$benchmark_pid_query_status"; return 65; }
  [[ "$benchmark_pid_before" =~ ^[0-9]+$ ]] || { echo "benchmark_process_gate=missing benchmark_process=$benchmark_process"; return 65; }
  echo "benchmark_pid_before=$benchmark_pid_before"
  local state_text="" terminal=false observer_interval_seconds=5.0 observer_tick=0
  local observer_file="$out_root/host_observer.txt"
  : >"$observer_file"
  while (( $(debug_token_monotonic_ms) < observer_deadline_ms )); do
    state_text="$(adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true)"
    observer_tick=$((observer_tick + 1))
    if (( observer_tick == 1 || observer_tick % 20 == 0 )); then
      {
        echo "observer_timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        echo "observer_interval_seconds=$observer_interval_seconds"
        echo "pid=$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n' || true)"
        echo "top_resumed=$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
        echo "thermal_begin"
        adb -s "$serial" shell dumpsys thermalservice 2>/dev/null | sed -n '1,80p' || true
        echo "thermal_end"
        echo "meminfo_begin"
        adb -s "$serial" shell dumpsys meminfo "$package" 2>/dev/null | sed -n '1,80p' || true
        echo "meminfo_end"
        echo "marker_tail_begin"
        adb -s "$serial" exec-out run-as "$package" tail -n 80 files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null || true
        echo "marker_tail_end"
      } >>"$observer_file"
    fi
    if printf '%s\n' "$state_text" | grep -Fxq "timestamp=$timestamp" && printf '%s\n' "$state_text" | grep -Eq '^status=(success|partial|failure|blocked|timeout|cancelled|skipped)$'; then
      terminal=true
      break
    fi
    set +e
    pid_after="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; pid_query_status=$?
    benchmark_pid_after="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_query_status=$?
    set -e
    (( pid_query_status <= 1 && benchmark_pid_query_status <= 1 )) || { echo "failure_class=harness_lifecycle_failure reason=pid_query_failed main_status=$pid_query_status benchmark_status=$benchmark_pid_query_status"; return 65; }
    resumed="$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
    process_gate="$(debug_token_observer_dual_running_process_gate "$pid_before" "$pid_after" "$benchmark_pid_before" "$benchmark_pid_after" "$resumed" "$debug_component")"
    [[ "$process_gate" == "ok" ]] || { echo "failure_class=harness_lifecycle_failure reason=$process_gate before=$pid_before after=${pid_after:-none} benchmark_before=$benchmark_pid_before benchmark_after=${benchmark_pid_after:-none} resumed=$resumed"; return 65; }
    sleep 0.25
  done
  printf '%s\n' "$state_text" >"$out_root/state.txt"
  [[ "$terminal" == true ]] || { echo "terminal_state=missing timestamp=$timestamp artifact=$out_root"; return 65; }
  local marker_history="" conversation_close_finished=false engine_close_finished=false terminal_cleanup_gate=waiting
  while (( $(debug_token_monotonic_ms) < observer_deadline_ms )); do
    marker_history="$(adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null || true)"
    conversation_close_finished=false
    engine_close_finished=false
    if printf '%s\n' "$marker_history" | awk -v ts="$timestamp" 'BEGIN{RS=""} $0 ~ "(^|\\n)timestamp=" ts "(\\n|$)" && $0 ~ /stage=close_finished/ && $0 ~ /target=conversation/ {found=1} END{exit !found}'; then conversation_close_finished=true; fi
    if printf '%s\n' "$marker_history" | awk -v ts="$timestamp" 'BEGIN{RS=""} $0 ~ "(^|\\n)timestamp=" ts "(\\n|$)" && $0 ~ /stage=close_finished/ && $0 ~ /target=engine/ {found=1} END{exit !found}'; then engine_close_finished=true; fi
    set +e
    pid_after="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; pid_query_status=$?
    benchmark_pid_after="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_query_status=$?
    set -e
    (( pid_query_status <= 1 && benchmark_pid_query_status <= 1 )) || { echo "failure_class=harness_lifecycle_failure reason=cleanup_pid_query_failed main_status=$pid_query_status benchmark_status=$benchmark_pid_query_status"; return 65; }
    terminal_cleanup_gate="$(debug_token_observer_dual_terminal_cleanup_gate "$pid_before" "$pid_after" "$benchmark_pid_before" "$benchmark_pid_after" "$conversation_close_finished" "$engine_close_finished")"
    case "$terminal_cleanup_gate" in
      closed|benchmark_process_terminated|processes_absent) break ;;
      waiting) sleep 1 ;;
      *) echo "failure_class=harness_lifecycle_failure reason=terminal_cleanup_$terminal_cleanup_gate"; return 65 ;;
    esac
  done
  [[ "$terminal_cleanup_gate" == "closed" || "$terminal_cleanup_gate" == "benchmark_process_terminated" || "$terminal_cleanup_gate" == "processes_absent" ]] || { echo "failure_class=harness_lifecycle_failure reason=terminal_cleanup_timeout"; return 70; }
  local success_process_gate
  success_process_gate="$(debug_token_observer_success_process_gate "$pid_before" "$pid_after" "$benchmark_pid_before" "$benchmark_pid_after" "$terminal_cleanup_gate" "$conversation_close_finished" "$engine_close_finished")"
  [[ "$success_process_gate" == "ok" ]] || { echo "failure_class=harness_lifecycle_failure reason=success_$success_process_gate"; return 65; }
  local csv_file md_file expected_csv_file expected_md_file status reason fallback_count timeout_count
  csv_file="$(printf '%s\n' "$state_text" | sed -n 's/^csv_file=//p' | head -1)"
  md_file="$(printf '%s\n' "$state_text" | sed -n 's/^markdown_file=//p' | head -1)"
  expected_csv_file="litert_lm_gpu_benchmark_${timestamp}.csv"
  expected_md_file="litert_lm_gpu_benchmark_${timestamp}.md"
  status="$(printf '%s\n' "$state_text" | sed -n 's/^status=//p' | head -1)"
  reason="$(printf '%s\n' "$state_text" | sed -n 's/^reason=//p' | head -1)"
  fallback_count="$(printf '%s\n' "$state_text" | sed -n 's/^fallback_count=//p' | head -1)"
  timeout_count="$(printf '%s\n' "$state_text" | sed -n 's/^timeout_count=//p' | head -1)"
  [[ "$csv_file" == "$expected_csv_file" && "$md_file" == "$expected_md_file" ]] || {
    echo "failure_class=harness_lifecycle_failure reason=terminal_artifact_name_mismatch csv=$csv_file markdown=$md_file expected_csv=$expected_csv_file expected_markdown=$expected_md_file"
    return 65
  }
  [[ "$csv_file" =~ ^[A-Za-z0-9._-]+$ && "$md_file" =~ ^[A-Za-z0-9._-]+$ ]] || {
    echo "terminal_artifact_names=invalid csv=$csv_file markdown=$md_file"
    return 65
  }
  for file in "$csv_file" "$md_file" "litert_lm_gpu_benchmark_environment_$timestamp.txt" "litert_lm_gpu_benchmark_marker_history.txt"; do
    [[ "$file" =~ ^[A-Za-z0-9._-]+$ ]] || continue
    adb -s "$serial" exec-out run-as "$package" cat "files/$file" >"$out_root/$file" 2>/dev/null || true
  done
  [[ -s "$out_root/$csv_file" && -s "$out_root/$md_file" ]] || {
    echo "failure_class=harness_lifecycle_failure reason=terminal_artifact_missing_or_empty csv=$csv_file markdown=$md_file"
    return 65
  }
  local artifact_validation
  if ! artifact_validation="$(debug_token_validate_terminal_artifacts "$timestamp" "$status" "$reason" "$out_root/$csv_file" "$out_root/$md_file")"; then
    echo "failure_class=harness_lifecycle_failure reason=terminal_artifact_identity_or_coherence_invalid validation=$artifact_validation"
    return 65
  fi
  [[ "$artifact_validation" == "ok" ]] || {
    echo "failure_class=harness_lifecycle_failure reason=terminal_artifact_identity_or_coherence_invalid validation=$artifact_validation"
    return 65
  }
  adb -s "$serial" shell dumpsys activity exit-info "$package" >"$post_exit" 2>&1 || true
  local benchmark_pid_final benchmark_pid_final_status benchmark_process_stable=false
  set +e
  pid_after="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; pid_query_status=$?
  benchmark_pid_final="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_final_status=$?
  set -e
  (( pid_query_status <= 1 && benchmark_pid_final_status <= 1 )) || {
    echo "failure_class=harness_lifecycle_failure reason=final_pid_query_failed main_status=$pid_query_status benchmark_status=$benchmark_pid_final_status"
    return 65
  }
  local fresh_crash=false process_stable=false intentional_process_cleanup=false
  [[ "$pid_after" == "$pid_before" ]] && process_stable=true
  [[ "$benchmark_pid_final" == "$benchmark_pid_before" ]] && benchmark_process_stable=true
  if ! cmp -s "$pre_exit" "$post_exit"; then fresh_crash=true; fi
  if [[ "$reason" == cancelled_by_debug_foreground_ui && -z "$pid_after" && -z "$benchmark_pid_final" ]]; then
    intentional_process_cleanup=true
    fresh_crash=false
  fi
  {
    echo "timestamp=$timestamp"
    echo "serial=$serial"
    echo "model=$model"
    echo "case=$case_name"
    echo "label=$label"
    echo "transport=foreground_ui_internal"
    echo "service_cold_start_used=false"
    echo "pid_before=$pid_before"
    echo "pid_after=${pid_after:-none}"
    echo "benchmark_pid_before=$benchmark_pid_before"
    echo "benchmark_pid_after=${benchmark_pid_after:-none}"
    echo "benchmark_pid_final=${benchmark_pid_final:-none}"
    echo "terminal_cleanup_gate=$terminal_cleanup_gate"
    echo "conversation_close_finished=$conversation_close_finished"
    echo "engine_close_finished=$engine_close_finished"
    echo "process_stable=$process_stable"
    echo "benchmark_process_stable=$benchmark_process_stable"
    echo "fresh_crash=$fresh_crash"
    echo "intentional_process_cleanup=$intentional_process_cleanup"
    echo "artifact_path=$out_root"
  } >"$out_root/host_gate.txt"
  echo "artifact_timestamp=$timestamp"
  echo "artifact_path=$out_root"
  cat "$out_root/host_gate.txt"
  cat "$out_root/state.txt"
  [[ -s "$out_root/$csv_file" ]] && sed -n '1,3p' "$out_root/$csv_file"
  [[ "$fresh_crash" == false && "$process_stable" == true && "$benchmark_process_stable" == true ]] || {
    echo "failure_class=harness_lifecycle_failure reason=process_or_crash_gate main_pid=${pid_after:-none} benchmark_pid=${benchmark_pid_final:-none}"
    return 65
  }
  [[ "$status" == success && "$reason" == completed && "$fallback_count" == 0 && "$timeout_count" == 0 ]] || {
    echo "benchmark_acceptance=failed status=$status reason=$reason fallback_count=$fallback_count timeout_count=$timeout_count"
    return 65
  }
)

single_nx733j_serial() {
  if ! debug_token_single_nx733j_device_gate; then
    return 65
  fi
  NX733J_SERIAL="$DEBUG_TOKEN_NX733J_SERIAL"
}

nx733j_serial_for_endpoint() {
  local host="$1" port="$2" observer_deadline_ms="${3:-}" serial connect_out state model
  validate_host "$host"
  validate_port "$port"
  serial="${host}:${port}"
  if [[ -n "$observer_deadline_ms" ]]; then
    connect_out="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb connect "$serial" 2>&1)" || { echo "endpoint_connect=failed serial=$serial detail=$connect_out" >&2; return 65; }
    state="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" get-state 2>/dev/null || true)"
    model="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
  else
    connect_out="$(timeout --signal=TERM --kill-after=1 15 adb connect "$serial" 2>&1)" || { echo "endpoint_connect=failed serial=$serial detail=$connect_out" >&2; return 65; }
    state="$(timeout --signal=TERM --kill-after=1 15 adb -s "$serial" get-state 2>/dev/null || true)"
    model="$(timeout --signal=TERM --kill-after=1 15 adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
  fi
  [[ "$connect_out" != *"failed"* && "$connect_out" != *"Failed"* ]] || { echo "endpoint_connect=failed serial=$serial detail=$connect_out" >&2; return 65; }
  [[ "$state" == "device" ]] || { echo "endpoint_gate=not_device serial=$serial" >&2; return 65; }
  [[ "$model" == "NX733J" ]] || { echo "endpoint_gate=model_mismatch serial=$serial model=${model:-missing}" >&2; return 65; }
  printf '%s\n' "$serial"
}

force_stop_debug_token_ui_benchmark() (
  local host="$1" port="$2" observer_max_seconds=600 observer_deadline_ms adb_bin
  observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"
  adb_bin="$(type -P adb)" || { echo "adb_binary=missing" >&2; return 65; }
  adb() { run_debug_token_ui_case_bounded_adb "$observer_deadline_ms" "$adb_bin" "$@"; }
  sleep() { run_debug_token_observer_bounded "$observer_deadline_ms" command sleep "$@"; }
  local serial package="io.github.ninbyo02.lami.gpunoconstraint" benchmark_process main_pid benchmark_pid main_pid_status benchmark_pid_status
  benchmark_process="$package:gpu_benchmark_probe"
  serial="$(nx733j_serial_for_endpoint "$host" "$port" "$observer_deadline_ms")" || { echo "debug_token_ui_force_stop=device_gate_blocked"; return 65; }
  adb -s "$serial" shell am force-stop "$package"
  sleep 1
  set +e
  main_pid="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; main_pid_status=$?
  benchmark_pid="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_status=$?
  set -e
  (( main_pid_status <= 1 && benchmark_pid_status <= 1 )) || { echo "debug_token_ui_force_stop=pid_query_failed main_status=$main_pid_status benchmark_status=$benchmark_pid_status"; return 65; }
  [[ -z "$main_pid" && -z "$benchmark_pid" ]] || { echo "debug_token_ui_force_stop=pid_still_running main_pid=${main_pid:-none} benchmark_pid=${benchmark_pid:-none}"; return 65; }
  echo "debug_token_ui_force_stop=completed main_pid=none benchmark_pid=none"
)

stop_debug_token_ui_benchmark() (
  local host="$1" port="$2" observer_max_seconds=600 observer_deadline_ms adb_bin
  observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"
  adb_bin="$(type -P adb)" || { echo "adb_binary=missing" >&2; return 65; }
  adb() { run_debug_token_ui_case_bounded_adb "$observer_deadline_ms" "$adb_bin" "$@"; }
  sleep() { run_debug_token_observer_bounded "$observer_deadline_ms" command sleep "$@"; }
  local serial package="io.github.ninbyo02.lami.gpunoconstraint" benchmark_process
  local component="$package/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity"
  local remote_xml="/data/local/tmp/lami_debug_token_stop.xml" local_xml bounds x1 y1 x2 y2 tap_x tap_y
  local state_text="" terminal=false main_pid benchmark_pid main_pid_status benchmark_pid_status
  benchmark_process="$package:gpu_benchmark_probe"
  serial="$(nx733j_serial_for_endpoint "$host" "$port" "$observer_deadline_ms")" || { echo "debug_token_ui_stop=device_gate_blocked"; return 65; }
  adb -s "$serial" shell am start -W -n "$component" >/dev/null || { echo "debug_token_ui_stop=activity_start_failed"; return 65; }
  adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
  local_xml="$(mktemp)"
  adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
  adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
  bounds="$(python3 - "$local_xml" <<'PY_STOP'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == 'Stop':
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if m:
            print(','.join(m.groups()))
            break
PY_STOP
)"
  rm -f "$local_xml"
  [[ "$bounds" =~ ^[0-9]+,[0-9]+,[0-9]+,[0-9]+$ ]] || { echo "debug_token_ui_stop=button_not_found"; return 65; }
  IFS=, read -r x1 y1 x2 y2 <<< "$bounds"
  tap_x=$(( (x1 + x2) / 2 )); tap_y=$(( (y1 + y2) / 2 ))
  adb -s "$serial" shell input tap "$tap_x" "$tap_y"
  echo "debug_token_ui_stop=ui_tapped x=$tap_x y=$tap_y"
  while (( $(debug_token_monotonic_ms) < observer_deadline_ms )); do
    state_text="$(adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true)"
    if printf '%s\n' "$state_text" | grep -q '^status=failure$' &&
       printf '%s\n' "$state_text" | grep -q '^reason=cancelled_by_debug_foreground_ui$'; then
      terminal=true
      break
    fi
    sleep 0.25
  done
  [[ "$terminal" == true ]] || { echo "debug_token_ui_stop=terminal_cancel_missing"; return 65; }
  adb -s "$serial" shell am force-stop "$package"
  sleep 1
  set +e
  main_pid="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; main_pid_status=$?
  benchmark_pid="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_status=$?
  set -e
  (( main_pid_status <= 1 && benchmark_pid_status <= 1 )) || { echo "debug_token_ui_stop=pid_query_failed main_status=$main_pid_status benchmark_status=$benchmark_pid_status"; return 65; }
  [[ -z "$main_pid" && -z "$benchmark_pid" ]] || { echo "debug_token_ui_stop=pid_still_running main_pid=${main_pid:-none} benchmark_pid=${benchmark_pid:-none}"; return 65; }
  echo "debug_token_ui_stop=terminal_cancelled_and_process_stopped main_pid=none benchmark_pid=none"
)

read_debug_token_ui_live_state() (
  local host="$1" port="$2" observer_max_seconds=600 observer_deadline_ms adb_bin
  observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"
  adb_bin="$(type -P adb)" || { echo "adb_binary=missing" >&2; return 65; }
  adb() { run_debug_token_ui_case_bounded_adb "$observer_deadline_ms" "$adb_bin" "$@"; }
  sleep() { run_debug_token_observer_bounded "$observer_deadline_ms" command sleep "$@"; }
  local package="io.github.ninbyo02.lami.gpunoconstraint" benchmark_process serial main_pid benchmark_pid main_pid_status benchmark_pid_status
  benchmark_process="$package:gpu_benchmark_probe"
  serial="$(nx733j_serial_for_endpoint "$host" "$port" "$observer_deadline_ms")" || { echo "debug_token_ui_live_state=device_gate_blocked"; return 65; }
  echo "debug_token_ui_live_state=begin"
  set +e
  main_pid="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; main_pid_status=$?
  benchmark_pid="$(adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_status=$?
  set -e
  (( main_pid_status <= 1 && benchmark_pid_status <= 1 )) || { echo "debug_token_ui_live_state=pid_query_failed main_status=$main_pid_status benchmark_status=$benchmark_pid_status"; return 65; }
  echo "debug_token_ui_pid=$main_pid"
  echo "debug_token_ui_benchmark_pid=$benchmark_pid"
  echo "debug_token_ui_top_resumed=$(adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
  adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true
  echo "debug_token_ui_marker_history_tail=begin"
  adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null | tail -120 || true
  echo "debug_token_ui_live_state=end"
)

run_debug_token_observer_bounded() {
  local deadline_ms="$1"
  shift
  local now_ms remaining_ms timeout_seconds
  now_ms="$(debug_token_monotonic_ms)"
  remaining_ms=$((deadline_ms - now_ms))
  (( remaining_ms > 0 )) || return 124
  printf -v timeout_seconds '%d.%03d' $((remaining_ms / 1000)) $((remaining_ms % 1000))
  timeout --signal=KILL "${timeout_seconds}s" "$@"
}

run_debug_token_ui_case_bounded_adb() {
  local deadline_ms="$1" adb_bin="$2"
  shift 2
  run_debug_token_observer_bounded "$deadline_ms" "$adb_bin" "$@"
}

observe_debug_token_ui_benchmark() {
  cd "$REPO"
  local host="$1" port="$2" serial package benchmark_process debug_component state marker marker_history timestamp timestamp_freshness
  local observer_max_seconds=600 observer_interval_seconds=5 out_root observer_file observer_deadline_ms
  local marker_freshness state_class initial_pid current_pid final_pid benchmark_pid_before benchmark_pid_current benchmark_pid_after initial_pid_query_status benchmark_pid_query_status
  local top_resumed process_gate cleanup_gate terminal=false
  local conversation_close_finished=false engine_close_finished=false
  observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"
  serial="$(nx733j_serial_for_endpoint "$host" "$port" "$observer_deadline_ms")" || fail
  package="io.github.ninbyo02.lami.gpunoconstraint"
  benchmark_process="$package:gpu_benchmark_probe"
  debug_component="$package/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity"
  state="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true)"
  timestamp="$(printf '%s\n' "$state" | sed -n 's/^timestamp=//p' | head -1)"
  [[ "$timestamp" =~ ^[0-9]{8}_[0-9]{6}$ ]] || { echo "marker_running_no_rerun=false reason=missing_active_timestamp" >&2; return 65; }
  timestamp_freshness="$(debug_token_observer_timestamp_freshness "$timestamp" "$(date +%s)" "$observer_max_seconds")"
  [[ "$timestamp_freshness" == "fresh" ]] || { echo "marker_running_no_rerun=false reason=timestamp_$timestamp_freshness timestamp=$timestamp" >&2; return 65; }
  state_class="$(debug_token_observer_state_class "$timestamp" "$state")"
  [[ "$state_class" == "running" ]] || { echo "marker_running_no_rerun=false reason=state_not_running state_class=$state_class timestamp=$timestamp" >&2; return 65; }
  marker="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker.txt 2>/dev/null || true)"
  marker_freshness="$(debug_token_observer_marker_freshness "$timestamp" "$marker" "$(date +%s)" "$observer_max_seconds")"
  [[ "$marker_freshness" == "fresh" ]] || { echo "marker_running_no_rerun=false reason=marker_$marker_freshness timestamp=$timestamp" >&2; return 65; }
  set +e
  initial_pid="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"; initial_pid_query_status=$?
  benchmark_pid_before="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"; benchmark_pid_query_status=$?
  set -e
  if (( initial_pid_query_status > 1 || benchmark_pid_query_status > 1 )); then
    printf 'reason=pid_query_failed\nmain_pid_query_status=%s\nbenchmark_pid_query_status=%s\n' "$initial_pid_query_status" "$benchmark_pid_query_status" >&2
    return 1
  fi
  top_resumed="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
  process_gate="$(debug_token_observer_dual_running_process_gate "$initial_pid" "$initial_pid" "$benchmark_pid_before" "$benchmark_pid_before" "$top_resumed" "$debug_component")"
  [[ "$process_gate" == "ok" ]] || { echo "marker_running_no_rerun=false reason=initial_$process_gate initial_pid=${initial_pid:-missing} benchmark_pid_before=${benchmark_pid_before:-missing} top_resumed=$top_resumed" >&2; return 65; }
  out_root="artifacts/litert_gpu_token_ui_observer/$timestamp"
  observer_file="$out_root/host_observer.txt"
  mkdir -p "$out_root"
  : >"$observer_file"
  echo "marker_running_no_rerun=true timestamp=$timestamp observer_max_seconds=$observer_max_seconds observer_interval_seconds=$observer_interval_seconds initial_pid=$initial_pid benchmark_pid_before=$benchmark_pid_before"
  while (( $(debug_token_monotonic_ms) < observer_deadline_ms )); do
    state="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true)"
    state_class="$(debug_token_observer_state_class "$timestamp" "$state")"
    local current_pid_query_status=0 benchmark_pid_query_status=0
    set +e
    current_pid="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"
    current_pid_query_status=$?
    benchmark_pid_current="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"
    benchmark_pid_query_status=$?
    set -e
    if (( current_pid_query_status > 1 || benchmark_pid_query_status > 1 )); then
      echo "observer_process_gate=pid_query_failed main_status=$current_pid_query_status benchmark_status=$benchmark_pid_query_status" >>"$observer_file"
      printf 'reason=pid_query_failed\nmain_pid_query_status=%s\nbenchmark_pid_query_status=%s\n' "$current_pid_query_status" "$benchmark_pid_query_status" >&2
      return 1
    fi
    top_resumed="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
    {
      echo "sample_epoch_ms=$(date +%s%3N)"
      echo "gpu_temperature_source=thermalservice"
      echo "initial_pid=$initial_pid"
      echo "pid=$current_pid"
      echo "benchmark_pid_before=$benchmark_pid_before"
      echo "benchmark_pid_current=$benchmark_pid_current"
      echo "$top_resumed"
      run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell dumpsys power 2>/dev/null | grep -E 'mWakefulness=|Display Power' | head -8 || true
      run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell dumpsys thermalservice 2>/dev/null | head -40 || true
      run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell dumpsys meminfo "$package" 2>/dev/null | head -40 || true
      printf '%s\n' "$state"
      echo "---"
    } >>"$observer_file"
    if [[ "$state_class" != "running" && "$state_class" != "terminal" ]]; then
      echo "observer_state_class=$state_class timestamp=$timestamp" >>"$observer_file"
      break
    fi
    if [[ "$state_class" == "terminal" ]]; then
      process_gate="$(debug_token_observer_dual_terminal_cleanup_gate "$initial_pid" "$current_pid" "$benchmark_pid_before" "$benchmark_pid_current" false false)"
    else
      process_gate="$(debug_token_observer_dual_running_process_gate "$initial_pid" "$current_pid" "$benchmark_pid_before" "$benchmark_pid_current" "$top_resumed" "$debug_component")"
    fi
    if [[ "$process_gate" != "ok" && "$process_gate" != "waiting" && "$process_gate" != "benchmark_process_terminated" && "$process_gate" != "processes_absent" ]]; then
      echo "observer_process_gate=$process_gate initial_pid=$initial_pid current_pid=${current_pid:-missing} benchmark_pid_before=$benchmark_pid_before benchmark_pid_current=${benchmark_pid_current:-missing} top_resumed=$top_resumed" >>"$observer_file"
      cp "$observer_file" "$out_root/host_observer-final.txt"
      echo "failure_class=harness_lifecycle_failure reason=$process_gate timestamp=$timestamp" >&2
      return 65
    fi
    if [[ "$state_class" == "terminal" ]]; then
      terminal=true
      final_pid="$current_pid"
      echo "observer_process_gate=$process_gate" >>"$observer_file"
      break
    fi
    run_debug_token_observer_bounded "$observer_deadline_ms" sleep "$observer_interval_seconds" || break
  done
  if [[ "$terminal" == true ]] && printf '%s\n' "$state" | grep -Fxq "timestamp=$timestamp"; then
    cleanup_gate="waiting"
    while (( $(debug_token_monotonic_ms) < observer_deadline_ms )); do
      marker_history="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null || true)"
      conversation_close_finished=false
      engine_close_finished=false
      if printf '%s\n' "$marker_history" | awk -v ts="$timestamp" 'BEGIN{RS=""} $0 ~ "(^|\\n)timestamp=" ts "(\\n|$)" && $0 ~ /stage=close_finished/ && $0 ~ /target=conversation/ {found=1} END{exit !found}'; then conversation_close_finished=true; fi
      if printf '%s\n' "$marker_history" | awk -v ts="$timestamp" 'BEGIN{RS=""} $0 ~ "(^|\\n)timestamp=" ts "(\\n|$)" && $0 ~ /stage=close_finished/ && $0 ~ /target=engine/ {found=1} END{exit !found}'; then engine_close_finished=true; fi
      local final_pid_query_status=0 final_benchmark_pid_query_status=0
      set +e
      final_pid="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\r\n')"
      final_pid_query_status=$?
      benchmark_pid_after="$(run_debug_token_observer_bounded "$observer_deadline_ms" adb -s "$serial" shell pidof "$benchmark_process" 2>/dev/null | tr -d '\r\n')"
      final_benchmark_pid_query_status=$?
      set -e
      if (( final_pid_query_status > 1 || final_benchmark_pid_query_status > 1 )); then
        printf 'reason=pid_query_failed\nmain_pid_query_status=%s\nbenchmark_pid_query_status=%s\n' "$final_pid_query_status" "$final_benchmark_pid_query_status" >&2
        return 1
      fi
      cleanup_gate="$(debug_token_observer_dual_terminal_cleanup_gate "$initial_pid" "$final_pid" "$benchmark_pid_before" "$benchmark_pid_after" "$conversation_close_finished" "$engine_close_finished")"
      echo "terminal_cleanup_gate=$cleanup_gate final_pid=${final_pid:-missing} benchmark_pid_after=${benchmark_pid_after:-missing} conversation_close_finished=$conversation_close_finished engine_close_finished=$engine_close_finished" >>"$observer_file"
      case "$cleanup_gate" in
        closed|benchmark_process_terminated|processes_absent) break ;;
        waiting) run_debug_token_observer_bounded "$observer_deadline_ms" sleep 1 || break ;;
        *) cp "$observer_file" "$out_root/host_observer-final.txt"; echo "failure_class=harness_lifecycle_failure reason=terminal_cleanup_$cleanup_gate" >&2; return 65 ;;
      esac
    done
    if [[ "$cleanup_gate" != "closed" && "$cleanup_gate" != "benchmark_process_terminated" && "$cleanup_gate" != "processes_absent" ]]; then
      cp "$observer_file" "$out_root/host_observer-final.txt"
      echo "failure_class=harness_lifecycle_failure reason=terminal_cleanup_timeout timestamp_matched_terminal=true terminal_cleanup=false observer_timeout=true"
      return 70
    fi
    {
      echo "timestamp_matched_terminal=true"
      echo "final_pid=${final_pid:-missing}"
      echo "benchmark_pid_after=${benchmark_pid_after:-missing}"
      echo "process_stable=$([[ "$final_pid" == "$initial_pid" ]] && echo true || echo false)"
      echo "benchmark_process_stable=$([[ "$benchmark_pid_after" == "$benchmark_pid_before" ]] && echo true || echo false)"
      echo "terminal_cleanup_gate=$cleanup_gate"
      echo "conversation_close_finished=$conversation_close_finished"
      echo "engine_close_finished=$engine_close_finished"
      echo "marker_history_for_terminal=begin"
      printf '%s\n' "$marker_history"
      echo "marker_history_for_terminal=end"
    } >>"$observer_file"
  else
    cp "$observer_file" "$out_root/host_observer-final.txt"
    echo "failure_class=harness_lifecycle_failure reason=terminal_state_timeout timestamp_matched_terminal=false observer_timeout=true"
    return 70
  fi
  cp "$observer_file" "$out_root/host_observer-final.txt"
  echo "timestamp_matched_terminal=true final_pid=${final_pid:-missing} benchmark_pid_after=${benchmark_pid_after:-missing} process_stable=$([[ "$final_pid" == "$initial_pid" ]] && echo true || echo false) benchmark_process_stable=$([[ "$benchmark_pid_after" == "$benchmark_pid_before" ]] && echo true || echo false) terminal_cleanup_gate=$cleanup_gate conversation_close_finished=$conversation_close_finished engine_close_finished=$engine_close_finished"
  echo "observer_file=$REPO/$observer_file"
}

read_debug_token_ui_artifact() {
  local timestamp="$1"
  [[ "$timestamp" =~ ^[0-9]{8}_[0-9]{6}$ ]] || fail
  cd "$REPO"
  local dir
  dir="$(find artifacts/debug_token_ui -mindepth 1 -maxdepth 1 -type d -name "*-$timestamp-*" -o -type d -name "${timestamp}-*" 2>/dev/null | head -1)"
  if [[ -z "$dir" ]]; then
    dir="$( (grep -rl "^timestamp=$timestamp$" artifacts/debug_token_ui/*/host_gate.txt 2>/dev/null || true) | head -1 | xargs -r dirname)"
  fi
  [[ -n "$dir" && "$(realpath -m "$dir")" == "$REPO/artifacts/debug_token_ui/"* ]] || fail
  local canonical_dir canonical_file size
  canonical_dir="$(realpath -e "$dir")" || fail
  echo "artifact_path=$canonical_dir"
  for file in host_gate.txt state.txt litert_lm_gpu_benchmark_environment_${timestamp}.txt litert_lm_gpu_benchmark_${timestamp}.csv litert_lm_gpu_benchmark_${timestamp}.md; do
    [[ -f "$dir/$file" && ! -L "$dir/$file" ]] || continue
    canonical_file="$(realpath -e "$dir/$file")" || fail
    [[ "$canonical_file" == "$canonical_dir/"* ]] || fail
    size="$(stat -c '%s' "$canonical_file")"
    [[ "$size" =~ ^[0-9]+$ && "$size" -le 1048576 ]] || { echo "artifact_file_size=blocked file=$file size=$size"; return 65; }
    echo "===== $file ====="
    dd if="$canonical_file" bs=1048576 count=1 status=none
  done
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
  read-lami-crash-log\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail; run_read_lami_crash_log "${parts[1]}" ;;
  read-lami-crash-log)
    run_read_lami_crash_log latest ;;
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
  emulator-open-sprite-editor-lami)
    run_emulator_open_sprite_editor_lami ;;
  emulator-screenshot-sprite-editor-lami)
    run_emulator_screenshot_sprite_editor_lami ;;
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
  debug-token-ui-run\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 4 ]] || fail
    run_debug_token_ui_case "${parts[1]}" "${parts[2]}" "${parts[3]}" ;;
  debug-token-ui-force-stop\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail
    force_stop_debug_token_ui_benchmark "${parts[1]}" "${parts[2]}" ;;
  debug-token-ui-stop\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail
    stop_debug_token_ui_benchmark "${parts[1]}" "${parts[2]}" ;;
  debug-token-ui-live-state\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail
    read_debug_token_ui_live_state "${parts[1]}" "${parts[2]}" ;;
  debug-token-ui-observe\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail
    observe_debug_token_ui_benchmark "${parts[1]}" "${parts[2]}" ;;
  debug-token-ui-artifact\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail
    read_debug_token_ui_artifact "${parts[1]}" ;;
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
  test-dirty-sprite-bitmap-ops)
    run_test_dirty_sprite_bitmap_ops ;;
  test-dirty-npu-only-model-selection)
    cd "$REPO"
    echo "== NPU-ONLY MODEL SELECTION DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests '*LocalInferenceModelSlotTest*' ;;
  test-dirty-npu-quality-repair)
    cd "$REPO"
    echo "== NPU QUALITY REPAIR DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests '*NpuStandardRouteS1MapperTest*' ;;
  test-dirty-runtime-gate-expectations)
    cd "$REPO"
    echo "== RUNTIME GATE EXPECTATIONS DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests 'io.github.ninbyo02.lami.StandardGpuNoConstraintProviderGradleConfigTest' \
      --tests 'io.github.ninbyo02.lami.ui.screens.settings.LocalInferenceResidencyPolicyTest' ;;
  test-standard-full)
    cd "$REPO"
    [[ -z "$(git status --porcelain --untracked-files=all)" ]] || { echo "worktree must be clean" >&2; exit 65; }
    [[ "$(git branch --show-current)" == "future" ]] || fail
    echo "== STANDARD FULL UNIT TEST =="
    ./gradlew --no-daemon :app:testStandardDebugUnitTest
    report="app/build/reports/tests/testStandardDebugUnitTest/index.html"
    total="$(sed -n 's/.*<div class="counter">\([0-9][0-9]*\)<\/div>.*/\1/p' "$report" | head -1)"
    echo "standard_unit_tests=$total" ;;
  test-dirty-standard-full)
    cd "$REPO"
    [[ "$(git branch --show-current)" == "future" ]] || fail
    echo "== STANDARD FULL DIRTY UNIT TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest --rerun-tasks
    report="app/build/reports/tests/testStandardDebugUnitTest/index.html"
    total="$(sed -n 's/.*<div class="counter">\([0-9][0-9]*\)<\/div>.*/\1/p' "$report" | head -1)"
    echo "standard_unit_tests=$total" ;;
  assemble-standard)
    cd "$REPO"
    [[ -z "$(git status --porcelain --untracked-files=all)" ]] || { echo "worktree must be clean" >&2; exit 65; }
    [[ "$(git branch --show-current)" == "future" ]] || fail
    echo "== STANDARD COMPILE/ASSEMBLE =="
    ./gradlew --no-daemon :app:compileStandardDebugKotlin :app:assembleStandardDebug
    apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
    [[ -s "$apk" ]] || { echo "missing APK: $apk" >&2; exit 65; }
    echo "apk=$apk"
    stat -c 'apk_size=%s apk_mtime=%y' "$apk"
    sha256sum "$apk" ;;
  assemble-dirty-standard)
    cd "$REPO"
    [[ "$(git branch --show-current)" == "future" ]] || fail
    echo "== STANDARD DIRTY ASSEMBLE =="
    git status --short --branch
    ./gradlew --no-daemon :app:assembleStandardDebug
    apk="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
    [[ -s "$apk" ]] || { echo "missing APK: $apk" >&2; exit 65; }
    echo "apk=$apk"
    stat -c 'apk_size=%s apk_mtime=%y' "$apk"
    sha256sum "$apk" ;;
  assemble-reviewed-gpu-30400-candidate\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 3 ]] || fail
    run_assemble_reviewed_gpu_30400_candidate "${parts[1]}" "${parts[2]}" ;;
  test-dirty-resident-router)
    cd "$REPO"
    echo "== RESIDENT CAPABILITY DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests '*LocalBackendRuntimeEvidenceTest*' \
      --tests '*NpuStandardRouteS1ProviderTest*' ;;
  test-dirty-startup-backend-check)
    cd "$REPO"
    echo "== STARTUP BACKEND CHECK DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest \
      --tests '*StartupBackendCheckSequenceTest*' ;;
  test-dirty-gpu-30400-cases)
    cd "$REPO"
    echo "== GPU 30400 FIXED CASE DIRTY TEST =="
    git status --short --branch
    bash scripts/tests/debug_token_observer_policy_test.sh
    bash scripts/tests/debug_token_controller_device_gate_test.sh
    ./gradlew --no-daemon :app:testStandardDebugUnitTest --rerun-tasks \
      --tests 'io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkUiSourceContractTest' \
      --tests 'io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkRunSummaryTest' ;;
  test-dirty-gpu-30400-kotlin-cases)
    cd "$REPO"
    echo "== GPU 30400 KOTLIN CONTRACT DIRTY TEST =="
    git status --short --branch
    ./gradlew --no-daemon :app:testStandardDebugUnitTest --rerun-tasks \
      --tests 'io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkUiSourceContractTest' \
      --tests 'io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkRunSummaryTest' ;;
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
  read-lami-crash-log [latest|lami-crash-raw-YYYYMMDD-HHMMSS.log|lami-crash-report-YYYYMMDD-HHMMSS.log|lami-startup-crash-YYYYMMDD-HHMMSS.log|lami-dropbox-crash-YYYYMMDD-HHMMSS.log]
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
  emulator-install-standard-lami # fixed standard debug build/install to emulator-5554
  emulator-start-standard-lami # fixed standard app launch on emulator-5554
  emulator-open-sprite-editor-lami # fixed navigation to Sprite Editor Resize dialog on emulator-5554
  emulator-screenshot-sprite-editor-lami # fixed Resize dialog PNG/XML readback from emulator-5554
  adb-pair <10.5.5.3|192.168.52.52> <pair-port> <6-digit-code>
  adb-connect <10.5.5.3|192.168.52.52> <connect-port>
  adb-start-app <10.5.5.3|192.168.52.52> <connect-port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment|trueEngineNpuProbe|standardGpuMinimalRuntimeCandidate|standardGpuNoConstraintProvider|gpunoconstraint|no-constraint]
  adb-dump-customnpu-diag <10.5.5.3|192.168.52.52> <connect-port>
  adb-dump-standardnpu-diag <10.5.5.3|192.168.52.52> <connect-port>
  debug-token-ui-run <192.168.52.52> <port> <gpu16|gpu32|gpu128|gpu512|gpu1024|gpu2048|gpu4096|gpu8192|gpu16384|gpu32768|gpu65536|gpu131072|gpu262144|gpu524288|gpu1048576|gpu-long-2048|gpu-long-8192|gpu-long-16384|gpu-long-22400|gpu-long-24576|gpu-long-28800|gpu-long-30400|gpu-long-32768|gpu-long-32769|gpu-long-30400-r80|gpu-long-30400-r825|gpu-long-30400-r85|gpu-long-30400-payload-22400|gpu-long-30400-payload-28800|gpu-long-32768-payload-30400-r85|gpu-long-30400-flow-compare|gpu-long-30400-typed-callback-compare|cpu32> # fixed foreground UI
  debug-token-ui-artifact <YYYYMMDD_HHMMSS> # bounded timestamp readback
  debug-token-ui-live-state <192.168.52.52> <port> # fixed read-only state/marker/PID readback
  debug-token-ui-observe <192.168.52.52> <port> # attach-only 5-second host observer, bounded to 600 seconds; never reruns
  debug-token-ui-stop <192.168.52.52> <port> # exact foreground Stop button tap on NX733J
  debug-token-ui-force-stop <192.168.52.52> <port> # package-specific fail-safe cleanup on NX733J
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
  test-dirty-sprite-bitmap-ops # fixed SpriteBitmapOpsTest, preserves dirty worktree
  test-dirty-npu-only-model-selection # fixed LocalInferenceModelSlotTest, preserves dirty worktree
  test-dirty-runtime-gate-expectations # fixed two-class focused test, preserves dirty worktree
  test-standard-full             # clean future standard unit regression, no reset
  test-dirty-standard-full       # dirty future full standard unit regression, no reset/clean/stash
  assemble-standard              # clean future standard compile/assemble, no reset
  assemble-dirty-standard        # dirty future standard assemble, no reset
  assemble-reviewed-gpu-30400-candidate <origin-future-commit> <patch-sha256> # reviewed t57 patch; assemble only, no device
  test-dirty-startup-backend-check # fixed focused test, preserves dirty worktree
  test-dirty-gpu-30400-cases # fixed 30400 matrix/evidence tests, preserves dirty worktree
  test-dirty-gpu-30400-kotlin-cases # Kotlin-only contract tests for RED/GREEN while shell fixtures are RED
  test-dirty-resident-router # fixed focused test, preserves dirty worktree
  compile-dirty-standard       # dirty worktree compile only, no reset/install
EOF
    ;;
  *)
    fail ;;
esac
