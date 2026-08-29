#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
endpoint=
apk=
app_id=io.github.ninbyo02.lami.npuvalidation
action=io.github.ninbyo02.lami.action.STANDARD_NPU_RELEASE_PREFLIGHT
receiver=io.github.ninbyo02.lami.ui.screens.home.StandardNpuReleasePreflightReceiver
mode=dispatch_api_preflight

while (($#)); do
  case "$1" in
    --endpoint) endpoint=${2:?missing endpoint}; shift 2 ;;
    --apk) apk=${2:?missing apk}; shift 2 ;;
    --app-id) app_id=${2:?missing app id}; shift 2 ;;
    --mode) mode=${2:?missing mode}; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$endpoint" && -f "$apk" ]] || {
  echo "usage: $0 --endpoint HOST:PORT --apk SIGNED_APK [--app-id ID]" >&2
  exit 2
}
timestamp=$(date +%Y%m%d_%H%M%S_%N)
out_dir="$root_dir/artifacts/standard_npu_release_preflight/$timestamp"
mkdir -p "$out_dir"
adb() { command adb -s "$endpoint" "$@"; }
adb connect "$endpoint" >"$out_dir/adb_connect.txt"
adb get-state | grep -Fxq device
adb install -r "$apk" >"$out_dir/adb_install.txt"
# Android may leave a replaced package in stopped=true even with
# --include-stopped-packages. Launch once to clear that state, return Home, and
# kill only the base process so the validation receiver still cold-starts in
# its isolated :npu_preflight process.
adb shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1 \
  >"$out_dir/package_unstop.txt" 2>&1
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME \
  >"$out_dir/home_after_unstop.txt" 2>&1
adb shell am kill "$app_id" >"$out_dir/base_process_kill.txt" 2>&1 || true
sleep 1
external_dir="/sdcard/Android/data/$app_id/files"
status_name=standard_npu_release_preflight_status.txt
result_name=qairt244_persistent_custom_jni_probe_result.txt
diag_name=qairt244_persistent_custom_jni_probe_diag.txt
adb shell rm -f \
  "$external_dir/$status_name" \
  "$external_dir/$result_name" \
  "$external_dir/$diag_name" || true
adb logcat -c
adb shell am broadcast --include-stopped-packages \
  -a "$action" \
  -n "$app_id/$receiver" \
  --es native_probe_mode "$mode" >"$out_dir/am_broadcast.txt"
status=
for _ in $(seq 1 45); do
  status=$(adb shell cat "$external_dir/$status_name" 2>/dev/null | tr -d '\r' || true)
  if grep -Eq '^status=(returned|failed)$' <<<"$status"; then
    break
  fi
  sleep 1
done
printf '%s\n' "$status" >"$out_dir/$status_name"
for name in "$result_name" "$diag_name"; do
  adb pull "$external_dir/$name" "$out_dir/$name" >/dev/null 2>&1 || true
done
adb logcat -b all -d -t 6000 >"$out_dir/logcat.txt" 2>/dev/null || true
pass=true
grep -Fxq 'status=returned' "$out_dir/$status_name" || pass=false
grep -Fxq 'native_return=completed' "$out_dir/$status_name" || pass=false
case "$mode" in
  dispatch_api_preflight)
    grep -Fq 'hypothesis_result=dispatch_api_preflight_success' \
      "$out_dir/$result_name" 2>/dev/null || pass=false
    for marker in \
      'dispatch_preflight_dlopen=success' \
      'dispatch_preflight_dlsym=success' \
      'dispatch_preflight_get_api_status=0' \
      'dispatch_preflight_interface=present'; do
      grep -Fq "$marker" "$out_dir/$diag_name" 2>/dev/null || pass=false
    done
    ;;
  dispatch_initialize_preflight)
    grep -Fq 'hypothesis_result=dispatch_initialize_preflight_success' \
      "$out_dir/$result_name" 2>/dev/null || pass=false
    for marker in \
      'dispatch_initialize_symbols=present' \
      'dispatch_initialize_status=0' \
      'dispatch_initialize_capabilities_status=0 capabilities=1' \
      'dispatch_initialize_device_context_status=0' \
      'dispatch_initialize_device_destroy_status=0'; do
      grep -Fq "$marker" "$out_dir/$diag_name" 2>/dev/null || pass=false
    done
    ;;
  *) pass=false ;;
esac
result=FAIL
[[ "$pass" == true ]] && result=PASS
{
  echo '# Standard NPU Release Preflight'
  echo
  echo "- Result: \`$result\`"
  echo "- Endpoint: \`$endpoint\`"
  echo "- App ID: \`$app_id\`"
  echo "- Mode: \`$mode\`"
  echo "- APK SHA-256: \`$(sha256sum "$apk" | cut -d' ' -f1)\`"
  echo "- Isolated process: \`:npu_preflight\`"
  echo "- Artifact: \`${out_dir#"$root_dir"/}\`"
} >"$out_dir/summary.md"
cat "$out_dir/summary.md"
[[ "$pass" == true ]]
