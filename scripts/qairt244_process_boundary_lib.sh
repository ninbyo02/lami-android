#!/usr/bin/env bash

qairt244_process_adb() {
  if [ -n "${QAIRT244_PROCESS_DEVICE_SERIAL:-}" ]; then
    adb -s "$QAIRT244_PROCESS_DEVICE_SERIAL" "$@"
  else
    adb "$@"
  fi
}

qairt244_process_boundary_classification() {
  local boundary="$1"
  local pid_file="$2"
  local ps_file="$3"
  if [ -s "$pid_file" ] || grep -Fq "${QAIRT244_PROCESS_PACKAGE:-io.github.ninbyo02.lami}" "$ps_file" 2>/dev/null; then
    printf 'PROCESS_PRESENT'
    return 0
  fi
  case "$boundary" in
    before_dispatch) printf 'PROCESS_ABSENT_BEFORE_DISPATCH' ;;
    after_dispatch) printf 'PROCESS_DISAPPEARED_AFTER_DISPATCH' ;;
    after_result_or_timeout|after_cleanup) printf 'PROCESS_DISAPPEARED_AFTER_CLEANUP' ;;
    after_10s) printf 'PROCESS_DISAPPEARED_AFTER_10S' ;;
    *) printf 'PROCESS_STATE_UNKNOWN' ;;
  esac
}

qairt244_process_can_dispatch() {
  local classification="$1"
  [ "$classification" != PROCESS_ABSENT_BEFORE_DISPATCH ] &&
    [ "$classification" != PROCESS_STATE_UNKNOWN ]
}

qairt244_process_is_suspect() {
  case "$1" in
    PROCESS_ABSENT_BEFORE_DISPATCH|PROCESS_DISAPPEARED_AFTER_DISPATCH|PROCESS_DISAPPEARED_AFTER_CLEANUP|PROCESS_DISAPPEARED_AFTER_10S)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

qairt244_process_logcat_marker() {
  local marker="$1"
  qairt244_process_adb shell log -t QAIRT244_PROCESS_BOUNDARY "$marker" >/dev/null 2>&1 || true
}

qairt244_process_boundary_snapshot() {
  local out_dir="$1"
  local package_name="$2"
  local prompt_index="$3"
  local slug="$4"
  local boundary="$5"
  local dir="$out_dir/process_boundary/prompt_${prompt_index}_${slug}/${boundary}"
  local classification can_dispatch reuse_allowed per_run_required suspect

  mkdir -p "$dir"
  QAIRT244_PROCESS_PACKAGE="$package_name"
  export QAIRT244_PROCESS_PACKAGE

  date +%Y-%m-%dT%H:%M:%S%z >"$dir/host_timestamp.txt"
  qairt244_process_adb shell date >"$dir/device_timestamp.txt" 2>&1 || true
  qairt244_process_logcat_marker "prompt=${prompt_index} slug=${slug} boundary=${boundary} begin"
  qairt244_process_adb shell pidof "$package_name" >"$dir/pidof.txt" 2>&1 || true
  qairt244_process_adb shell ps -A >"$dir/ps_all.txt" 2>&1 || true
  grep -F "$package_name" "$dir/ps_all.txt" >"$dir/ps_package.txt" 2>/dev/null || true
  qairt244_process_adb shell dumpsys activity processes >"$dir/dumpsys_activity_processes.txt" 2>&1 || true
  grep -F -n -C 8 "$package_name" "$dir/dumpsys_activity_processes.txt" >"$dir/dumpsys_activity_processes_package_context.txt" 2>/dev/null || true
  qairt244_process_adb shell dumpsys activity top >"$dir/dumpsys_activity_top.txt" 2>&1 || true
  qairt244_process_adb shell dumpsys window visible-apps >"$dir/dumpsys_window_visible_apps.txt" 2>&1 || true
  qairt244_process_adb logcat -d -t 1200 >"$dir/logcat_slice.txt" 2>&1 || true
  grep -E "ActivityTaskManager|ActivityManager|am_proc_died|ProcessRecord|Killing|LowMemoryKiller|lowmemorykiller|lmkd|FATAL EXCEPTION|ANR|tombstone|SIGSEGV|SIGABRT|${package_name}|QAIRT244_PROCESS_BOUNDARY" \
    "$dir/logcat_slice.txt" >"$dir/logcat_process_markers.txt" 2>/dev/null || true

  classification="$(qairt244_process_boundary_classification "$boundary" "$dir/pidof.txt" "$dir/ps_package.txt")"
  if qairt244_process_can_dispatch "$classification"; then
    can_dispatch=true
  else
    can_dispatch=false
  fi
  if qairt244_process_is_suspect "$classification"; then
    suspect=true
    reuse_allowed=false
    per_run_required=true
  else
    suspect=false
    reuse_allowed=true
    per_run_required=false
  fi

  {
    printf 'prompt_index=%s\n' "$prompt_index"
    printf 'slug=%s\n' "$slug"
    printf 'boundary=%s\n' "$boundary"
    printf 'package=%s\n' "$package_name"
    printf 'pidof=%s\n' "$(tr '\n' ' ' <"$dir/pidof.txt" | sed 's/[[:space:]]*$//')"
    printf 'classification=%s\n' "$classification"
    printf 'can_dispatch=%s\n' "$can_dispatch"
    printf 'process_disappeared_suspect=%s\n' "$suspect"
    printf 'reuse_allowed=%s\n' "$reuse_allowed"
    printf 'hidden_per_run_isolated_required=%s\n' "$per_run_required"
  } >"$dir/summary.txt"
  cat "$dir/summary.txt"
}

qairt244_process_append_boundary_table_header() {
  local dest="$1"
  {
    printf '# Process boundary results\n\n'
    printf '| prompt_index | slug | boundary | classification | can_dispatch | process_disappeared_suspect | reuse_allowed | hidden_per_run_isolated_required | pidof |\n'
    printf '| ---: | --- | --- | --- | --- | --- | --- | --- | --- |\n'
  } >"$dest"
}

qairt244_process_append_boundary_table_row() {
  local snapshot_summary="$1"
  local dest="$2"
  local prompt_index slug boundary classification can_dispatch suspect reuse per_run pidof
  prompt_index="$(awk -F= '$1 == "prompt_index" { print $2 }' "$snapshot_summary")"
  slug="$(awk -F= '$1 == "slug" { print $2 }' "$snapshot_summary")"
  boundary="$(awk -F= '$1 == "boundary" { print $2 }' "$snapshot_summary")"
  classification="$(awk -F= '$1 == "classification" { print $2 }' "$snapshot_summary")"
  can_dispatch="$(awk -F= '$1 == "can_dispatch" { print $2 }' "$snapshot_summary")"
  suspect="$(awk -F= '$1 == "process_disappeared_suspect" { print $2 }' "$snapshot_summary")"
  reuse="$(awk -F= '$1 == "reuse_allowed" { print $2 }' "$snapshot_summary")"
  per_run="$(awk -F= '$1 == "hidden_per_run_isolated_required" { print $2 }' "$snapshot_summary")"
  pidof="$(awk -F= '$1 == "pidof" { print $2 }' "$snapshot_summary")"
  printf '| %s | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
    "$prompt_index" "$slug" "$boundary" "$classification" "$can_dispatch" "$suspect" "$reuse" "$per_run" "${pidof:-none}" >>"$dest"
}
