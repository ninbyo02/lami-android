#!/usr/bin/env bash

# Pure policy helpers for the fixed foreground benchmark observer. This file is
# intentionally side-effect free so its timestamp/state decisions can be tested
# without ADB or a device.

debug_token_observer_state_value() {
  local key="$1"
  local state_text="$2"
  local line
  while IFS= read -r line; do
    if [[ "$line" == "$key="* ]]; then
      printf '%s\n' "${line#*=}"
      return 0
    fi
  done <<< "$state_text"
  return 1
}

debug_token_observer_state_class() {
  local expected_timestamp="$1"
  local state_text="$2"
  local actual_timestamp status
  actual_timestamp="$(debug_token_observer_state_value timestamp "$state_text" 2>/dev/null || true)"
  status="$(debug_token_observer_state_value status "$state_text" 2>/dev/null || true)"

  if [[ "$actual_timestamp" != "$expected_timestamp" ]]; then
    printf 'stale\n'
    return 0
  fi
  case "$status" in
    running) printf 'running\n' ;;
    success|partial|failure|blocked|timeout|cancelled|skipped) printf 'terminal\n' ;;
    *) printf 'invalid\n' ;;
  esac
}

debug_token_observer_timestamp_freshness() {
  local timestamp="$1"
  local now_epoch="$2"
  local max_age_seconds="$3"
  local timestamp_epoch age

  if [[ ! "$timestamp" =~ ^[0-9]{8}_[0-9]{6}$ ]] ||
     [[ ! "$now_epoch" =~ ^[0-9]+$ ]] ||
     [[ ! "$max_age_seconds" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
    return 0
  fi
  timestamp_epoch="$(date -d "${timestamp:0:4}-${timestamp:4:2}-${timestamp:6:2} ${timestamp:9:2}:${timestamp:11:2}:${timestamp:13:2}" +%s 2>/dev/null || true)"
  if [[ ! "$timestamp_epoch" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
    return 0
  fi
  age=$((now_epoch - timestamp_epoch))
  if (( age < 0 )); then
    printf 'future\n'
  elif (( age > max_age_seconds )); then
    printf 'stale\n'
  else
    printf 'fresh\n'
  fi
}

debug_token_observer_marker_freshness() {
  local expected_timestamp="$1"
  local marker_text="$2"
  local now_epoch="$3"
  local max_age_seconds="$4"
  local marker_timestamp marker_stage wall_time_ms wall_epoch age

  marker_timestamp="$(debug_token_observer_state_value timestamp "$marker_text" 2>/dev/null || true)"
  [[ "$marker_timestamp" == "$expected_timestamp" ]] || { printf 'missing\n'; return 0; }
  marker_stage="$(debug_token_observer_state_value stage "$marker_text" 2>/dev/null || true)"
  case "$marker_stage" in
    engine_create_started|engine_created|conversation_create_started|conversation_created|prompt_started|contents_created|callback_send_started|callback_first_token) ;;
    *) printf 'invalid_stage\n'; return 0 ;;
  esac
  wall_time_ms="$(debug_token_observer_state_value wall_time_ms "$marker_text" 2>/dev/null || true)"
  if [[ ! "$wall_time_ms" =~ ^[0-9]+$ ]] ||
     [[ ! "$now_epoch" =~ ^[0-9]+$ ]] ||
     [[ ! "$max_age_seconds" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
    return 0
  fi
  wall_epoch=$((wall_time_ms / 1000))
  age=$((now_epoch - wall_epoch))
  if (( age < 0 )); then
    printf 'future\n'
  elif (( age > max_age_seconds )); then
    printf 'stale\n'
  else
    printf 'fresh\n'
  fi
}

debug_token_observer_remaining_seconds() {
  local start_epoch="$1" now_epoch="$2" max_seconds="$3"
  local remaining
  if [[ ! "$start_epoch" =~ ^[0-9]+$ ]] ||
     [[ ! "$now_epoch" =~ ^[0-9]+$ ]] ||
     [[ ! "$max_seconds" =~ ^[0-9]+$ ]]; then
    printf '0\n'
    return 0
  fi
  remaining=$((start_epoch + max_seconds - now_epoch))
  (( remaining > 0 )) || remaining=0
  printf '%s\n' "$remaining"
}

debug_token_monotonic_ms() {
  awk '{ printf "%d\n", $1 * 1000 }' /proc/uptime
}

debug_token_observer_running_process_gate() {
  local initial_pid="$1" current_pid="$2" top_resumed="$3" exact_component="$4" observed_component="" token
  if [[ -z "$initial_pid" || -z "$current_pid" ]]; then
    printf 'pid_missing\n'
  elif [[ "$current_pid" != "$initial_pid" ]]; then
    printf 'pid_replaced\n'
  else
    top_resumed="${top_resumed//$'\r'/ }"
    top_resumed="${top_resumed//$'\n'/ }"
    for token in $top_resumed; do
      token="${token#topResumedActivity=}"
      token="${token%\}}"
      if [[ "$token" == "$exact_component" ]]; then
        observed_component="$token"
        break
      fi
    done
    if [[ "$observed_component" != "$exact_component" ]]; then
      printf 'foreground_lost\n'
    else
      printf 'ok\n'
    fi
  fi
}

debug_token_observer_terminal_cleanup_gate() {
  local initial_pid="$1" final_pid="$2" conversation_close_finished="$3" engine_close_finished="$4"
  if [[ -z "$final_pid" ]]; then
    printf 'pid_absent\n'
  elif [[ ! "$initial_pid" =~ ^[0-9]+$ || ! "$final_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ "$final_pid" != "$initial_pid" ]]; then
    printf 'pid_replaced\n'
  elif [[ "$conversation_close_finished" == "true" && "$engine_close_finished" == "true" ]]; then
    printf 'closed\n'
  else
    printf 'waiting\n'
  fi
}

debug_token_observer_dual_running_process_gate() {
  local initial_main_pid="$1" current_main_pid="$2" initial_benchmark_pid="$3" current_benchmark_pid="$4"
  local top_resumed="$5" exact_component="$6" foreground_gate
  if [[ -z "$initial_main_pid" || -z "$current_main_pid" ]]; then
    printf 'main_pid_missing\n'
  elif [[ ! "$initial_main_pid" =~ ^[0-9]+$ || ! "$current_main_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ "$current_main_pid" != "$initial_main_pid" ]]; then
    printf 'main_pid_replaced\n'
  elif [[ -z "$initial_benchmark_pid" || -z "$current_benchmark_pid" ]]; then
    printf 'benchmark_pid_missing\n'
  elif [[ ! "$initial_benchmark_pid" =~ ^[0-9]+$ || ! "$current_benchmark_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ "$current_benchmark_pid" != "$initial_benchmark_pid" ]]; then
    printf 'benchmark_pid_replaced\n'
  else
    foreground_gate="$(debug_token_observer_running_process_gate "$initial_main_pid" "$current_main_pid" "$top_resumed" "$exact_component")"
    printf '%s\n' "$foreground_gate"
  fi
}

debug_token_observer_dual_terminal_cleanup_gate() {
  local initial_main_pid="$1" final_main_pid="$2" initial_benchmark_pid="$3" final_benchmark_pid="$4"
  local conversation_close_finished="$5" engine_close_finished="$6"
  if [[ ! "$initial_main_pid" =~ ^[0-9]+$ || ! "$initial_benchmark_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ -n "$final_main_pid" && ! "$final_main_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ -n "$final_benchmark_pid" && ! "$final_benchmark_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ -n "$final_main_pid" && "$final_main_pid" != "$initial_main_pid" ]]; then
    printf 'main_pid_replaced\n'
  elif [[ -n "$final_benchmark_pid" && "$final_benchmark_pid" != "$initial_benchmark_pid" ]]; then
    printf 'benchmark_pid_replaced\n'
  elif [[ -z "$final_benchmark_pid" && ( -z "$final_main_pid" || "$final_main_pid" == "$initial_main_pid" ) ]]; then
    if [[ -z "$final_main_pid" ]]; then
      printf 'processes_absent\n'
    else
      printf 'benchmark_process_terminated\n'
    fi
  elif [[ "$conversation_close_finished" == "true" && "$engine_close_finished" == "true" ]]; then
    printf 'closed\n'
  else
    printf 'waiting\n'
  fi
}

debug_token_observer_success_process_gate() {
  local initial_main_pid="$1" final_main_pid="$2" initial_benchmark_pid="$3" final_benchmark_pid="$4"
  local cleanup_gate="$5" conversation_close_finished="$6" engine_close_finished="$7"
  if [[ -z "$initial_main_pid" || -z "$final_main_pid" ]]; then
    printf 'main_pid_missing\n'
  elif [[ ! "$initial_main_pid" =~ ^[0-9]+$ || ! "$final_main_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ "$final_main_pid" != "$initial_main_pid" ]]; then
    printf 'main_pid_replaced\n'
  elif [[ -z "$initial_benchmark_pid" || -z "$final_benchmark_pid" ]]; then
    printf 'benchmark_pid_missing\n'
  elif [[ ! "$initial_benchmark_pid" =~ ^[0-9]+$ || ! "$final_benchmark_pid" =~ ^[0-9]+$ ]]; then
    printf 'invalid\n'
  elif [[ "$final_benchmark_pid" != "$initial_benchmark_pid" ]]; then
    printf 'benchmark_pid_replaced\n'
  elif [[ "$cleanup_gate" != "closed" || "$conversation_close_finished" != "true" || "$engine_close_finished" != "true" ]]; then
    printf 'cleanup_not_closed\n'
  else
    printf 'ok\n'
  fi
}

debug_token_single_nx733j_device_gate() {
  local connected_serials connected_count serial model
  DEBUG_TOKEN_NX733J_SERIAL=""
  connected_serials="$(adb devices | awk '$2 == "device" { print $1 }')" || return 65
  connected_count="$(printf '%s\n' "$connected_serials" | awk 'NF { count++ } END { print count + 0 }')"
  [[ "$connected_count" == "1" ]] || {
    echo "single_nx733j_serial=device_gate_blocked connected_device_count=$connected_count" >&2
    return 65
  }
  serial="$(printf '%s\n' "$connected_serials" | awk 'NF { print; exit }')"
  [[ "$(adb -s "$serial" get-state 2>/dev/null)" == "device" ]] || {
    echo "single_nx733j_serial=state_gate_blocked serial=$serial" >&2
    return 65
  }
  model="$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  [[ "$model" == "NX733J" ]] || {
    echo "single_nx733j_serial=model_gate_blocked serial=$serial model=${model:-missing}" >&2
    return 65
  }
  DEBUG_TOKEN_NX733J_SERIAL="$serial"
}

debug_token_validate_terminal_artifacts() {
  local expected_timestamp="$1" expected_status="$2" expected_reason="$3" csv_path="$4" markdown_path="$5"
  python3 - "$expected_timestamp" "$expected_status" "$expected_reason" "$csv_path" "$markdown_path" <<'PY'
import csv
import os
import re
import sys

expected_timestamp, expected_status, expected_reason, csv_path, markdown_path = sys.argv[1:]
valid = bool(re.fullmatch(r"[0-9]{8}_[0-9]{6}", expected_timestamp))
valid = valid and expected_status in {"success", "partial", "failure", "blocked", "timeout", "cancelled", "skipped"}
for path in (csv_path, markdown_path):
    valid = valid and os.path.isfile(path) and not os.path.islink(path) and os.path.getsize(path) > 0
try:
    with open(csv_path, newline="", encoding="utf-8", errors="strict") as handle:
        records = list(csv.reader(handle))
    if len(records) != 2 or len(records[0]) != len(records[1]) or len(set(records[0])) != len(records[0]):
        valid = False
        row = {}
    else:
        row = dict(zip(records[0], records[1]))
    valid = valid and row.get("timestamp") == expected_timestamp
    valid = valid and row.get("status") == expected_status
    valid = valid and row.get("reason") == expected_reason
    with open(markdown_path, encoding="utf-8", errors="strict") as handle:
        markdown_lines = set(handle.read().splitlines())
    valid = valid and f"- timestamp: `{expected_timestamp}`" in markdown_lines
    valid = valid and f"- status: `{expected_status}`" in markdown_lines
    valid = valid and f"- reason: `{expected_reason}`" in markdown_lines
except (OSError, UnicodeError, csv.Error):
    valid = False
print("ok" if valid else "invalid")
sys.exit(0 if valid else 65)
PY
}
