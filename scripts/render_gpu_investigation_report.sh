#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
QUALITY_MATRIX="$ROOT_DIR/artifacts/gpu_output_quality_matrix"
APK_NATIVE_DIFF="$ROOT_DIR/artifacts/apk_native_diff"
OUTPUT="$ROOT_DIR/artifacts/gpu_investigation_report/GPU_INVESTIGATION_REPORT.md"

usage() {
  cat <<'USAGE'
Usage:
  scripts/render_gpu_investigation_report.sh \
    [--device-runs DIR] \
    [--quality-matrix DIR] \
    [--apk-native-diff DIR] \
    [--output FILE]

  scripts/render_gpu_investigation_report.sh --self-test

Renders a single Markdown report from copied device diagnostics and static
investigation artifacts. Missing inputs are recorded in the report and are not
fatal.
USAGE
}

latest_file_in_dir() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  find "$dir" -type f \
    ! -name 'GPU_CORRUPTION_REGRESSION_SUMMARY.md' \
    ! -name 'GPU_INVESTIGATION_REPORT.md' \
    -printf '%T@ %p\n' 2>/dev/null |
    sort -nr |
    awk 'NR == 1 { $1 = ""; sub(/^ /, ""); print; exit }'
}

latest_file_with_key() {
  local dir="$1"
  local key="$2"
  [[ -d "$dir" ]] || return 0
  find "$dir" -type f -printf '%T@ %p\n' 2>/dev/null |
    sort -nr |
    while IFS= read -r line; do
      local file
      file="${line#* }"
      if grep -Eq "(^|[[:space:]])${key}=" "$file" 2>/dev/null; then
        printf '%s\n' "$file"
        return 0
      fi
    done
}

append_key_table() {
  local out="$1"
  local file="$2"
  shift 2
  local key value
  {
    printf '| Key | Value |\n'
    printf '| --- | --- |\n'
    for key in "$@"; do
      value="$(diagnostic_get_key_or_unavailable "$file" "$key")"
      printf '| `%s` | `%s` |\n' "$key" "$value"
    done
  } >>"$out"
}

append_file_or_missing() {
  local out="$1"
  local title="$2"
  local file="$3"
  local max_lines="${4:-120}"
  {
    printf '### %s\n\n' "$title"
    if [[ -f "$file" ]]; then
      printf 'Source: `%s`\n\n' "$file"
      printf '```text\n'
      sed -n "1,${max_lines}p" "$file"
      printf '```\n\n'
    else
      printf 'missing: `%s`\n\n' "$file"
    fi
  } >>"$out"
}

capture_command() {
  local out_file="$1"
  shift
  if "$@" >"$out_file" 2>&1; then
    return 0
  fi
  {
    printf 'COMMAND_FAILED=true\n'
    printf 'COMMAND='
    printf '%q ' "$@"
    printf '\n'
  } >>"$out_file"
  return 0
}

promotion_blocker_from_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    printf 'unknown\n'
    return
  }
  local blocker quality gate callback_stage source_stage
  blocker="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_quality_promotion_blocker")"
  quality="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_quality_candidate_result")"
  gate="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_quality_gate_status")"
  callback_stage="$(diagnostic_get_key_or_unavailable "$file" "callback_corruption_earliest_stage")"
  source_stage="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_source_corruption_stage")"
  if [[ "$blocker" == "true" ||
    "$quality" == "quality_candidate_fail" ||
    "$gate" == "fail" ||
    "$callback_stage" == "raw_callback" ||
    "$source_stage" == "raw_callback" ]]; then
    printf 'true\n'
  elif [[ "$blocker" == "false" || "$quality" == "quality_candidate_pass" || "$gate" == "pass" ]]; then
    printf 'false\n'
  else
    printf 'unknown\n'
  fi
}

public_api_gap_from_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    printf 'unavailable\n'
    return
  }
  local existing runtime_config backend_constraint preferred_engine gpu_options artisan artisan_symbol kv_cache
  existing="$(diagnostic_get_key_or_unavailable "$file" "PUBLIC_API_GAP_SUMMARY")"
  if [[ "$existing" != "unavailable" && -n "$existing" ]]; then
    printf '%s\n' "$existing"
    return
  fi
  runtime_config="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_runtime_config_class_present")"
  backend_constraint="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_backend_constraint_class_present")"
  preferred_engine="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_preferred_engine_type_class_present")"
  gpu_options="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_gpu_options_class_present")"
  artisan="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_artisan_class_present")"
  artisan_symbol="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_llm_gpu_artisan_executor_symbol_present")"
  kv_cache="$(diagnostic_get_key_or_unavailable "$file" "gpu_internal_kv_cache_symbol_present")"
  if [[ "$runtime_config" == "false" &&
    "$backend_constraint" == "false" &&
    "$preferred_engine" == "false" &&
    "$gpu_options" == "false" &&
    "$artisan" == "false" &&
    "$artisan_symbol" == "true" &&
    "$kv_cache" == "true" ]]; then
    printf 'public_selector_api_absent_native_executor_symbols_present\n'
  else
    printf 'unavailable\n'
  fi
}

promotion_decision_reason_from_files() {
  local latest_file="$1"
  local internal_file="$2"
  local blocker public_gap quality callback_stage source_stage
  blocker="$(promotion_blocker_from_file "$latest_file")"
  public_gap="$(public_api_gap_from_file "$internal_file")"
  quality="$(diagnostic_get_key_or_unavailable "$latest_file" "gpu_output_quality_candidate_result")"
  callback_stage="$(diagnostic_get_key_or_unavailable "$latest_file" "callback_corruption_earliest_stage")"
  source_stage="$(diagnostic_get_key_or_unavailable "$latest_file" "gpu_output_source_corruption_stage")"
  if [[ "$blocker" == "true" ]]; then
    if [[ ( "$quality" == "quality_candidate_fail" || "$callback_stage" == "raw_callback" || "$source_stage" == "raw_callback" ) &&
      "$public_gap" == "public_selector_api_absent_native_executor_symbols_present" ]]; then
      printf 'raw_callback_corruption_and_public_api_gap\n'
    elif [[ "$quality" == "quality_candidate_fail" || "$callback_stage" == "raw_callback" || "$source_stage" == "raw_callback" ]]; then
      printf 'raw_callback_corruption\n'
    elif [[ "$public_gap" == "public_selector_api_absent_native_executor_symbols_present" ]]; then
      printf 'public_api_gap\n'
    else
      printf 'promotion_blocker_true\n'
    fi
  elif [[ "$blocker" == "false" ]]; then
    printf 'quality_gate_pass_requires_repeat_soak\n'
  else
    printf 'insufficient_diagnostics\n'
  fi
}

render_report() {
  local device_runs="$1"
  local quality_matrix="$2"
  local apk_native_diff="$3"
  local output="$4"
  local tmpdir latest_device executor_file promotion_blocker
  local internal_surface_file
  local promotion_decision promotion_decision_reason public_api_gap safe_next_action

  mkdir -p "$(dirname "$output")"
  tmpdir="$(mktemp -d)"
  REPORT_TMPDIR="$tmpdir"
  trap 'rm -rf "${REPORT_TMPDIR:-}"' RETURN

  latest_device="$(latest_file_in_dir "$device_runs" || true)"
  executor_file="$(latest_file_with_key "$device_runs" "edge_gallery_executor_probe_result" || true)"
  [[ -n "$executor_file" ]] || executor_file="$latest_device"
  internal_surface_file="$(latest_file_with_key "$device_runs" "gpu_internal_surface_probe_enabled" || true)"
  [[ -n "$internal_surface_file" ]] || internal_surface_file="$executor_file"
  promotion_blocker="$(promotion_blocker_from_file "$latest_device")"
  public_api_gap="$(public_api_gap_from_file "$internal_surface_file")"
  promotion_decision="unknown"
  if [[ "$promotion_blocker" == "true" ]]; then
    promotion_decision="blocked"
  elif [[ "$promotion_blocker" == "false" ]]; then
    promotion_decision="not_blocked_by_latest_run"
  fi
  promotion_decision_reason="$(promotion_decision_reason_from_files "$latest_device" "$internal_surface_file")"
  safe_next_action="keep_gpu_experimental_return_focus_to_cpu_stable_and_npu_route"

  {
    printf '# GPU Investigation Report\n\n'
    printf 'Generated by `scripts/render_gpu_investigation_report.sh`.\n\n'
    printf '## Overview\n\n'
    printf '| Input | Status | Path |\n'
    printf '| --- | --- | --- |\n'
    printf '| device runs | %s | `%s` |\n' "$([[ -d "$device_runs" ]] && printf present || printf missing)" "$device_runs"
    printf '| quality matrix | %s | `%s` |\n' "$([[ -d "$quality_matrix" ]] && printf present || printf missing)" "$quality_matrix"
    printf '| APK native diff | %s | `%s` |\n' "$([[ -d "$apk_native_diff" ]] && printf present || printf missing)" "$apk_native_diff"
    printf '| latest device run | %s | `%s` |\n' "$([[ -n "$latest_device" ]] && printf present || printf missing)" "${latest_device:-none}"
    printf '| executor probe file | %s | `%s` |\n' "$([[ -n "$executor_file" ]] && printf present || printf missing)" "${executor_file:-none}"
    printf '| internal surface probe file | %s | `%s` |\n\n' "$([[ -n "$internal_surface_file" ]] && printf present || printf missing)" "${internal_surface_file:-none}"
  } >"$output"

  {
    printf '## Latest Device Run Summary\n\n'
    if [[ -n "$latest_device" && -f "$latest_device" ]]; then
      printf 'Source: `%s`\n\n' "$latest_device"
    else
      printf 'missing: no device run diagnostics found.\n\n'
    fi
  } >>"$output"
  if [[ -n "$latest_device" && -f "$latest_device" ]]; then
    append_key_table "$output" "$latest_device" \
      selected_backend route_family status failure_stage \
      callback_corruption_earliest_stage gpu_output_source_corruption_stage \
      gpu_output_quality_candidate_result gpu_output_quality_gate_status \
      gpu_output_quality_promotion_blocker gpu_sampler_root_cause_candidate \
      gpu_fragmentation_score callback_quality_classification
    printf '\n' >>"$output"
  fi

  {
    printf '## GPU Output Quality Summary\n\n'
    if [[ -d "$quality_matrix" && -x "$SCRIPT_DIR/summarize_gpu_output_quality_matrix.sh" ]]; then
      capture_command "$tmpdir/quality_matrix.txt" "$SCRIPT_DIR/summarize_gpu_output_quality_matrix.sh" --input "$quality_matrix"
      printf '```text\n'
      cat "$tmpdir/quality_matrix.txt"
      printf '```\n\n'
    else
      printf 'missing: `%s`\n\n' "$quality_matrix"
    fi
  } >>"$output"

  {
    printf '## Executor Probe Classification\n\n'
    if [[ -n "$executor_file" && -f "$executor_file" && -x "$SCRIPT_DIR/classify_gpu_executor_probe_result.sh" ]]; then
      capture_command "$tmpdir/executor_classification.txt" "$SCRIPT_DIR/classify_gpu_executor_probe_result.sh" --input "$executor_file"
      printf 'Source: `%s`\n\n' "$executor_file"
      printf '```text\n'
      cat "$tmpdir/executor_classification.txt"
      printf '```\n\n'
    else
      printf 'missing: no executor probe diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## GPU Internal Surface Probe Summary\n\n'
    if [[ -n "$internal_surface_file" && -f "$internal_surface_file" ]]; then
      printf 'Source: `%s`\n\n' "$internal_surface_file"
    else
      printf 'missing: no `gpu_internal_surface_probe_*` diagnostics found.\n\n'
    fi
  } >>"$output"
  if [[ -n "$internal_surface_file" && -f "$internal_surface_file" ]]; then
    append_key_table "$output" "$internal_surface_file" \
      gpu_internal_surface_probe_enabled \
      gpu_internal_surface_probe_result \
      gpu_internal_surface_probe_disabled_reason \
      gpu_internal_runtime_config_class_present \
      gpu_internal_backend_constraint_class_present \
      gpu_internal_preferred_engine_type_class_present \
      gpu_internal_gpu_options_class_present \
      gpu_internal_artisan_class_present \
      gpu_internal_llm_gpu_artisan_executor_symbol_present \
      gpu_internal_kv_cache_symbol_present \
      gpu_internal_runtime_config_methods \
      gpu_internal_backend_constraint_methods \
      gpu_internal_gpu_options_methods \
      gpu_internal_probe_exception_class \
      gpu_internal_probe_exception_message
    {
      printf '\n'
      printf 'Interpretation:\n\n'
      printf '%s\n' '- Public/internal Java classes reported as absent point to a public API gap, not a safe selector surface.'
      printf '%s\n' '- `LlmGpuArtisanExecutor` and KV-cache native symbols reported as present point to native/internal executor capability.'
      printf '%s\n\n' '- If promotion blocker remains true, treat this as hidden/native executor path mismatch evidence, not a promotion signal.'
    } >>"$output"
  fi

  {
    printf '## Runtime/Native Stack Fingerprint Summary\n\n'
  } >>"$output"
  append_file_or_missing "$output" "native_stack_fingerprint.txt" "$apk_native_diff/native_stack_fingerprint.txt" 120

  {
    printf '## APK Native Diff Summary\n\n'
  } >>"$output"
  append_file_or_missing "$output" "runtime_stack_summary.txt" "$apk_native_diff/runtime_stack_summary.txt" 120
  append_file_or_missing "$output" "internal_surface_summary.txt" "$apk_native_diff/internal_surface_summary.txt" 80
  append_file_or_missing "$output" "internal_surface_diff.tsv" "$apk_native_diff/internal_surface_diff.tsv" 80
  append_file_or_missing "$output" "native_lib_inventory.tsv" "$apk_native_diff/native_lib_inventory.tsv" 80
  append_file_or_missing "$output" "jni_symbol_diff.tsv" "$apk_native_diff/jni_symbol_diff.tsv" 80

  {
    printf '## Executor Selection Static Trace Summary\n\n'
    printf 'This section is generated from static APK/directory token traces. Token presence is evidence of available runtime/native surfaces, not proof of the runtime executor selected by a device run.\n\n'
  } >>"$output"
  append_file_or_missing "$output" "executor_selection_trace_summary.txt" "$apk_native_diff/executor_selection_trace_summary.txt" 80
  append_file_or_missing "$output" "edge_only_executor_tokens.txt" "$apk_native_diff/edge_only_executor_tokens.txt" 80
  append_file_or_missing "$output" "lami_only_executor_tokens.txt" "$apk_native_diff/lami_only_executor_tokens.txt" 80
  append_file_or_missing "$output" "common_executor_tokens.txt" "$apk_native_diff/common_executor_tokens.txt" 120
  append_file_or_missing "$output" "executor_selection_trace.tsv" "$apk_native_diff/executor_selection_trace.tsv" 80

  {
    printf '## Regression Suite Summary\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/summarize_gpu_regression_results.sh" ]]; then
      capture_command "$tmpdir/regression_stdout.txt" "$SCRIPT_DIR/summarize_gpu_regression_results.sh" --input "$device_runs" --output "$tmpdir/GPU_CORRUPTION_REGRESSION_SUMMARY.md"
      if [[ -f "$tmpdir/GPU_CORRUPTION_REGRESSION_SUMMARY.md" ]]; then
        cat "$tmpdir/GPU_CORRUPTION_REGRESSION_SUMMARY.md"
        printf '\n'
      else
        printf '```text\n'
        cat "$tmpdir/regression_stdout.txt"
        printf '```\n\n'
      fi
    else
      printf 'missing: `%s`\n\n' "$device_runs"
    fi
  } >>"$output"

  {
    printf '## GPU Promotion Decision\n\n'
    printf '```text\n'
    printf 'GPU_PROMOTION_DECISION=%s\n' "$promotion_decision"
    printf 'GPU_PROMOTION_DECISION_REASON=%s\n' "$promotion_decision_reason"
    printf 'GPU_SAFE_NEXT_ACTION=%s\n' "$safe_next_action"
    printf 'PUBLIC_API_GAP_SUMMARY=%s\n' "$public_api_gap"
    printf '```\n\n'
  } >>"$output"

  {
    printf '## Promotion Blocker Status\n\n'
    printf '| Signal | Value |\n'
    printf '| --- | --- |\n'
    printf '| promotion_blocker_from_latest_device_run | `%s` |\n' "$promotion_blocker"
    if [[ -n "$latest_device" && -f "$latest_device" ]]; then
      printf '| gpu_output_quality_candidate_result | `%s` |\n' "$(diagnostic_get_key_or_unavailable "$latest_device" gpu_output_quality_candidate_result)"
      printf '| callback_corruption_earliest_stage | `%s` |\n' "$(diagnostic_get_key_or_unavailable "$latest_device" callback_corruption_earliest_stage)"
      printf '| gpu_output_source_corruption_stage | `%s` |\n' "$(diagnostic_get_key_or_unavailable "$latest_device" gpu_output_source_corruption_stage)"
      printf '| gpu_sampler_root_cause_candidate | `%s` |\n' "$(diagnostic_get_key_or_unavailable "$latest_device" gpu_sampler_root_cause_candidate)"
    fi
    printf '\n'
    if [[ "$promotion_blocker" == "true" ]]; then
      printf 'Status: **blocked**. Keep standard GPU promotion disabled.\n\n'
    elif [[ "$promotion_blocker" == "false" ]]; then
      printf 'Status: **not blocked by latest run**. Repeat stability and quality suite before changing any promotion gate.\n\n'
    else
      printf 'Status: **unknown**. Missing diagnostics; do not promote.\n\n'
    fi
  } >>"$output"

  cat >>"$output" <<'MARKDOWN'
## Root Cause Ranking

| Rank | Candidate | Current confidence |
| ---: | --- | ---: |
| 1 | GPU_ARTISAN / internal executor selection mismatch | 72% |
| 2 | hidden RuntimeConfig / backend constraint / preferred engine type difference | 66% |
| 3 | GPU KV cache / decode cache path difference | 56% |
| 4 | native runtime stack difference | 46% |
| 5 | callback semantics / hidden aggregation layer | 28% |
| 6 | sampler setting difference | 18% |
| 7 | maxTokens / cacheDir / public ConversationConfig difference | 16% |
| 8 | model file difference | 8% |

## Next Actions

1. If `Promotion Blocker Status` is blocked, do not promote standard GPU.
2. If executor probe classification says `same_stack_different_executor`, focus on internal executor/backend selection evidence.
3. If APK native diff says runtime stack differs, compare target library SHA-256 values before any further runtime experiment.
4. If regression summary says `gpu_only_corrupt` or `long_text_only_corrupt`, collect raw callback artifacts for those prompts.
5. If quality passes in one run, repeat app restart, short/medium/long, Markdown, mixed-language, and multi-turn tests before any Phase 2 discussion.
MARKDOWN

  printf 'Wrote GPU investigation report to: %s\n' "$output"
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  mkdir -p "$tmpdir/device_runs" "$tmpdir/quality_matrix" "$tmpdir/apk_native_diff"
  cat >"$tmpdir/device_runs/gpu_executor_probe.txt" <<'EOF'
selected_backend=GPU route_family=local_gpu status=success edge_gallery_executor_probe_result=same_sampler_different_executor executor_selection_fingerprint=execB loaded_native_runtime_stack_fingerprint=stackA gpu_output_quality_candidate_result=quality_candidate_fail callback_corruption_earliest_stage=raw_callback gpu_output_source_corruption_stage=raw_callback gpu_sampler_root_cause_candidate=runtime_decode_fragmentation gpu_output_quality_gate_status=fail gpu_output_quality_promotion_blocker=true gpu_fragmentation_score=0.812 callback_quality_classification=severe_fragmentation gpu_internal_surface_probe_enabled=true gpu_internal_surface_probe_result=completed gpu_internal_surface_probe_disabled_reason=none gpu_internal_runtime_config_class_present=false gpu_internal_backend_constraint_class_present=false gpu_internal_preferred_engine_type_class_present=false gpu_internal_gpu_options_class_present=false gpu_internal_artisan_class_present=false gpu_internal_llm_gpu_artisan_executor_symbol_present=true gpu_internal_kv_cache_symbol_present=true gpu_internal_runtime_config_methods=class_absent gpu_internal_backend_constraint_methods=class_absent gpu_internal_gpu_options_methods=class_absent gpu_internal_probe_exception_class=none gpu_internal_probe_exception_message=none
EOF
  cat >"$tmpdir/quality_matrix/baseline.txt" <<'EOF'
gpu_output_quality_matrix_mode=baseline gpu_output_quality_candidate_result=quality_candidate_fail gpu_fragmentation_score=0.812 average_chunk_length=1.7 one_char_chunk_ratio=0.52 gpu_output_suspicious_fragment_tail_ratio=0.18 gpu_sampler_root_cause_candidate=runtime_decode_fragmentation
EOF
  cat >"$tmpdir/apk_native_diff/runtime_stack_summary.txt" <<'EOF'
runtime_stack_same=no
jni_surface_same=yes
executor_symbol_same=no
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
EOF
  cat >"$tmpdir/apk_native_diff/native_stack_fingerprint.txt" <<'EOF'
EDGE_RUNTIME_STACK_FINGERPRINT=edge
LAMI_RUNTIME_STACK_FINGERPRINT=lami
EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=edge_internal
LAMI_INTERNAL_SURFACE_FINGERPRINT=lami_internal
EOF
  cat >"$tmpdir/apk_native_diff/internal_surface_summary.txt" <<'EOF'
EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=edge_internal
LAMI_INTERNAL_SURFACE_FINGERPRINT=lami_internal
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
EOF
  cat >"$tmpdir/apk_native_diff/internal_surface_diff.tsv" <<'EOF'
surface_hit	edge_present	lami_present
liblitertlm_jni.so:GPU_ARTISAN	yes	yes
liblitertlm_jni.so:LrtCreateGpuOptionsFromToml	yes	no
EOF
  cat >"$tmpdir/apk_native_diff/native_lib_inventory.tsv" <<'EOF'
library	edge_present	edge_size	edge_sha256	lami_present	lami_size	lami_sha256	same_sha256
libLiteRt.so	yes	10	edge	yes	10	lami	no
EOF
  cat >"$tmpdir/apk_native_diff/jni_symbol_diff.tsv" <<'EOF'
symbol	edge_present	lami_present
nativeGenerateContent	yes	yes
EOF
  cat >"$tmpdir/apk_native_diff/executor_selection_trace_summary.txt" <<'EOF'
EDGE_GALLERY_EXECUTOR_SELECTION_TRACE_FINGERPRINT=edge_exec_trace
LAMI_EXECUTOR_SELECTION_TRACE_FINGERPRINT=lami_exec_trace
EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=different_executor_selection_tokens
EDGE_ONLY_EXECUTOR_TOKENS=GPU_ARTISAN
LAMI_ONLY_EXECUTOR_TOKENS=BackendConstraint
COMMON_EXECUTOR_TOKENS=LlmGpuArtisanExecutor,tflite_gpu_kv_cache,nativeGenerateContentStream
EOF
  cat >"$tmpdir/apk_native_diff/edge_only_executor_tokens.txt" <<'EOF'
GPU_ARTISAN
EOF
  cat >"$tmpdir/apk_native_diff/lami_only_executor_tokens.txt" <<'EOF'
BackendConstraint
EOF
  cat >"$tmpdir/apk_native_diff/common_executor_tokens.txt" <<'EOF'
LlmGpuArtisanExecutor
tflite_gpu_kv_cache
nativeGenerateContentStream
EOF
  cat >"$tmpdir/apk_native_diff/executor_selection_trace.tsv" <<'EOF'
token	edge_present	lami_present	edge_count	lami_count	edge_sample	lami_sample
GPU_ARTISAN	yes	no	1	0	EDGE:GPU_ARTISAN	missing
BackendConstraint	no	yes	0	1	missing	LAMI:BackendConstraint
LlmGpuArtisanExecutor	yes	yes	1	1	EDGE:LlmGpuArtisanExecutor	LAMI:LlmGpuArtisanExecutor
EOF

  render_report "$tmpdir/device_runs" "$tmpdir/quality_matrix" "$tmpdir/apk_native_diff" "$tmpdir/report.md" >/tmp/lami_gpu_report_self_test.out
  grep -Fq '## Overview' "$tmpdir/report.md" || {
    echo "self-test failed: missing Overview" >&2
    exit 1
  }
  grep -Fq '## Promotion Blocker Status' "$tmpdir/report.md" || {
    echo "self-test failed: missing Promotion Blocker Status" >&2
    exit 1
  }
  grep -Fq '## GPU Internal Surface Probe Summary' "$tmpdir/report.md" || {
    echo "self-test failed: missing GPU Internal Surface Probe Summary" >&2
    exit 1
  }
  grep -Fq 'gpu_internal_llm_gpu_artisan_executor_symbol_present' "$tmpdir/report.md" || {
    echo "self-test failed: missing internal surface evidence" >&2
    exit 1
  }
  grep -Fq 'PUBLIC_API_GAP_SUMMARY=public_selector_api_absent_native_executor_symbols_present' "$tmpdir/report.md" || {
    echo "self-test failed: missing public API gap summary" >&2
    exit 1
  }
  grep -Fq 'GPU_PROMOTION_DECISION=blocked' "$tmpdir/report.md" || {
    echo "self-test failed: missing blocked promotion decision" >&2
    exit 1
  }
  grep -Fq 'GPU_PROMOTION_DECISION_REASON=raw_callback_corruption_and_public_api_gap' "$tmpdir/report.md" || {
    echo "self-test failed: missing promotion decision reason" >&2
    exit 1
  }
  grep -Fq 'GPU_SAFE_NEXT_ACTION=keep_gpu_experimental_return_focus_to_cpu_stable_and_npu_route' "$tmpdir/report.md" || {
    echo "self-test failed: missing GPU safe next action" >&2
    exit 1
  }
  grep -Fq 'Status: **blocked**' "$tmpdir/report.md" || {
    echo "self-test failed: expected blocked status" >&2
    exit 1
  }
  grep -Fq 'internal_surface_summary.txt' "$tmpdir/report.md" || {
    echo "self-test failed: expected internal surface summary artifact" >&2
    exit 1
  }
  grep -Fq '## Executor Selection Static Trace Summary' "$tmpdir/report.md" || {
    echo "self-test failed: missing executor selection static trace section" >&2
    exit 1
  }
  grep -Fq 'EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=different_executor_selection_tokens' "$tmpdir/report.md" || {
    echo "self-test failed: missing executor selection trace summary" >&2
    exit 1
  }

  render_report "$tmpdir/missing_device" "$tmpdir/missing_quality" "$tmpdir/missing_apk" "$tmpdir/missing_report.md" >/tmp/lami_gpu_report_missing_self_test.out
  grep -Fq 'missing' "$tmpdir/missing_report.md" || {
    echo "self-test failed: missing-input report did not record missing inputs" >&2
    exit 1
  }

  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device-runs)
      DEVICE_RUNS="${2:?missing --device-runs value}"
      shift 2
      ;;
    --quality-matrix)
      QUALITY_MATRIX="${2:?missing --quality-matrix value}"
      shift 2
      ;;
    --apk-native-diff)
      APK_NATIVE_DIFF="${2:?missing --apk-native-diff value}"
      shift 2
      ;;
    --output)
      OUTPUT="${2:?missing --output value}"
      shift 2
      ;;
    --self-test)
      run_self_test
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

render_report "$DEVICE_RUNS" "$QUALITY_MATRIX" "$APK_NATIVE_DIFF" "$OUTPUT"
