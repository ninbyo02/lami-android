#!/usr/bin/env bash

qairt244_lifecycle_first_run_id() {
  local file
  for file in "$@"; do
    [ -f "$file" ] || continue
    awk '
      {
        for (i = 1; i <= NF; i++) {
          if ($i ~ /^runId=/) {
            sub(/^runId=/, "", $i)
            print $i
            found=1
            exit
          }
        }
      }
      END { exit found ? 0 : 1 }
    ' "$file" 2>/dev/null && return 0
  done
  return 1
}

qairt244_lifecycle_observed_run_ids() {
  local file
  for file in "$@"; do
    [ -f "$file" ] || continue
    awk '
      {
        for (i = 1; i <= NF; i++) {
          if ($i ~ /^runId=/) {
            value=$i
            sub(/^runId=/, "", value)
            if (!seen[value]++) {
              print value
            }
          }
        }
      }
    ' "$file" 2>/dev/null
  done | awk 'NF && !seen[$0]++ { printf "%s%s", sep, $0; sep="," } END { if (sep == "") printf "unavailable"; printf "\n" }'
}

qairt244_lifecycle_run_id_mismatch() {
  local expected="$1"
  shift
  local file observed
  [ -n "$expected" ] && [ "$expected" != unavailable ] || return 1
  for file in "$@"; do
    [ -f "$file" ] || continue
    while IFS= read -r observed; do
      [ -n "$observed" ] || continue
      [ "$observed" = "$expected" ] || return 0
    done < <(awk '{ for (i = 1; i <= NF; i++) if ($i ~ /^runId=/) { sub(/^runId=/, "", $i); print $i } }' "$file" 2>/dev/null)
  done
  return 1
}

qairt244_lifecycle_bool_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 1
  awk -F= -v key="$key" '$1 == key { value=$2; found=1 } END { if (found) print value; else exit 1 }' "$file"
}

qairt244_lifecycle_cleanup_elapsed_ms() {
  local run_dir="$1"
  local value file
  for file in "$run_dir/result.txt" "$run_dir/native_diag.txt" "$run_dir/ui_cleanup_state.txt" "$run_dir/receiver_state.txt"; do
    value="$(qairt244_lifecycle_bool_value cleanup_elapsed_ms "$file" 2>/dev/null || true)"
    if [ -n "$value" ] && [ "$value" != unavailable ]; then
      printf '%s\n' "$value"
      return 0
    fi
  done
  printf 'missing\n'
}

qairt244_lifecycle_engine_close_evidence() {
  local run_dir="$1"
  if grep -Fq 'Engine.close=unique_ptr_cleanup' \
    "$run_dir/result.txt" "$run_dir/native_diag.txt" "$run_dir/ui_cleanup_state.txt" 2>/dev/null; then
    printf 'true\n'
  else
    printf 'false\n'
  fi
}

qairt244_lifecycle_native_completed() {
  local run_dir="$1"
  if grep -Eq ' success | result=success|npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag|Engine\.close=unique_ptr_cleanup' \
    "$run_dir/native_diag.txt" "$run_dir/result.txt" 2>/dev/null; then
    printf 'true\n'
  else
    printf 'false\n'
  fi
}

qairt244_lifecycle_result_completed() {
  local run_dir="$1"
  if grep -Eq '(^|[[:space:]])state=(success|failure)([[:space:]]|$)|^result=(success|failure)$|^receiver_result_success=(true|false)$' \
    "$run_dir/result.txt" "$run_dir/receiver_state.txt" 2>/dev/null; then
    printf 'true\n'
  else
    printf 'false\n'
  fi
}

qairt244_lifecycle_stale_result() {
  local run_dir="$1"
  if grep -Eq '(^|[[:space:]])stale_result(_detected)?=true([[:space:]]|$)' "$run_dir/result.txt" "$run_dir/receiver_state.txt" 2>/dev/null; then
    printf 'true\n'
  else
    printf 'false\n'
  fi
}

qairt244_lifecycle_timeout() {
  local run_dir="$1"
  local wait_status="$2"
  if [ "$wait_status" = timeout ] ||
    grep -Eq '(^|[[:space:]])state=timeout([[:space:]]|$)|(^|[[:space:]])timeout=true([[:space:]]|$)' \
      "$run_dir/result.txt" "$run_dir/receiver_state.txt" 2>/dev/null; then
    printf 'true\n'
  else
    printf 'false\n'
  fi
}

qairt244_lifecycle_summary_lines() {
  local run_dir="$1"
  local wait_status="$2"
  local execution_isolation="${3:-unknown}"
  local expected observed cleanup engine_close native_completed result_completed stale timeout classification
  local mismatch suspect reuse per_run_required result_rejected completed_accepted next_prompt_allowed runtime_reuse_policy

  expected="$(qairt244_lifecycle_first_run_id "$run_dir/result.txt" "$run_dir/receiver_state.txt" "$run_dir/native_diag.txt" 2>/dev/null || printf 'unavailable')"
  observed="$(qairt244_lifecycle_observed_run_ids "$run_dir/result.txt" "$run_dir/receiver_state.txt" "$run_dir/native_diag.txt" "$run_dir/ui_cleanup_state.txt")"
  cleanup="$(qairt244_lifecycle_cleanup_elapsed_ms "$run_dir")"
  engine_close="$(qairt244_lifecycle_engine_close_evidence "$run_dir")"
  native_completed="$(qairt244_lifecycle_native_completed "$run_dir")"
  result_completed="$(qairt244_lifecycle_result_completed "$run_dir")"
  stale="$(qairt244_lifecycle_stale_result "$run_dir")"
  timeout="$(qairt244_lifecycle_timeout "$run_dir" "$wait_status")"
  mismatch=false
  if qairt244_lifecycle_run_id_mismatch "$expected" "$run_dir/result.txt" "$run_dir/receiver_state.txt" "$run_dir/native_diag.txt" "$run_dir/ui_cleanup_state.txt"; then
    mismatch=true
  fi

  if [ "$mismatch" = true ]; then
    classification=RUN_ID_MISMATCH_REJECTED
  elif [ "$stale" = true ]; then
    classification=STALE_RESULT_REJECTED
  elif [ "$timeout" = true ]; then
    classification=TIMEOUT_SUSPECT
  elif [ "$result_completed" != true ] || [ "$native_completed" != true ] || [ "$cleanup" = missing ] || [ "$engine_close" != true ]; then
    classification=CLEANUP_MISSING_SUSPECT
  elif grep -Eq '(^|[[:space:]])state=failure([[:space:]]|$)|^result=failure$|^receiver_result_success=false$' "$run_dir/result.txt" "$run_dir/receiver_state.txt" 2>/dev/null; then
    classification=FAILURE_CLEAN
  else
    classification=SUCCESS_CLEAN
  fi

  case "$classification" in
    SUCCESS_CLEAN)
      suspect=false
      reuse=true
      per_run_required=false
      result_rejected=false
      completed_accepted=true
      next_prompt_allowed=true
      runtime_reuse_policy=reuse_allowed
      ;;
    *)
      if [ "$classification" = FAILURE_CLEAN ]; then
        suspect=false
      else
        suspect=true
      fi
      reuse=false
      per_run_required=true
      result_rejected=true
      completed_accepted=false
      next_prompt_allowed=false
      runtime_reuse_policy=per_run_isolated_required
      ;;
  esac

  printf 'lifecycle_classification=%s\n' "$classification"
  printf 'expected_run_id=%s\n' "$expected"
  printf 'observed_run_id=%s\n' "$observed"
  printf 'cleanup_elapsed_ms=%s\n' "$cleanup"
  printf 'engine_close_evidence=%s\n' "$engine_close"
  printf 'native_completed_evidence=%s\n' "$native_completed"
  printf 'result_completed_evidence=%s\n' "$result_completed"
  printf 'suspect_session=%s\n' "$suspect"
  printf 'reuse_allowed=%s\n' "$reuse"
  printf 'next_prompt_allowed=%s\n' "$next_prompt_allowed"
  printf 'runtime_reuse_policy=%s\n' "$runtime_reuse_policy"
  printf 'per_run_isolated_required=%s\n' "$per_run_required"
  printf 'hidden_per_run_isolated_required=%s\n' "$per_run_required"
  printf 'stale_result_rejected=%s\n' "$stale"
  printf 'run_id_mismatch_rejected=%s\n' "$mismatch"
  printf 'result_rejected=%s\n' "$result_rejected"
  printf 'completed_result_accepted=%s\n' "$completed_accepted"
  printf 'lifecycle_execution_isolation=%s\n' "$execution_isolation"
}
