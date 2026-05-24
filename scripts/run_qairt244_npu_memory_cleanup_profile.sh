#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmokeActivity"
ARTIFACT="artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526"
MODEL_PATH="/data/user/0/$APP_ID/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=3
TIMEOUT_SECONDS=30
MARKER="qairt244_short_multitoken_smoke_v1"

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact)
      ARTIFACT="${2:-}"
      shift 2
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:-30}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_memory_cleanup_profile.sh [--artifact <custom-build-artifact>] [--timeout <seconds>]

Runs exactly one isolated customBuildExperimentDebug QAIRT short multi-token
smoke with maxOutputTokens=3, captures Android runtime/native memory snapshots
before install, after install, before smoke launch, immediately after smoke,
3 seconds after smoke, and 10 seconds after smoke.

This script does not connect NPU to the normal UI, does not set selectedPath=npu
on the normal route, does not call high-level generateResponse, and does not run
streaming generation.
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_memory_cleanup_profile/$TIMESTAMP"
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-npu-memory-profile] %s\n' "$*"
}

safe_adb_shell() {
  adb shell "$@" 2>&1 || true
}

write_policy() {
  cat >"$OUT_DIR/large_artifacts_local_only.md" <<'EOF'
# Large Artifacts Are Local-Only

This memory profile references local QAIRT/LiteRT native build artifacts, but
does not need to commit rebuilt `.so` files. Commit summaries, result files,
meminfo text, Build IDs, hashes, and diagnostics only.
EOF
}

verify_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  local device_line
  device_line="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ {print $1; exit}' "$OUT_DIR/adb_devices.txt")"
  if [ -z "$device_line" ]; then
    {
      printf 'result=blocked\n'
      printf 'reason=no-nubia-or-non-emulator-adb-device\n'
    } >"$OUT_DIR/result.txt"
    return 1
  fi
  printf '%s\n' "$device_line" >"$OUT_DIR/selected_device.txt"
  {
    printf 'ro.product.model='
    safe_adb_shell getprop ro.product.model | tr -d '\r'
    printf 'ro.product.device='
    safe_adb_shell getprop ro.product.device | tr -d '\r'
    printf 'ro.soc.model='
    safe_adb_shell getprop ro.soc.model | tr -d '\r'
    printf 'ro.board.platform='
    safe_adb_shell getprop ro.board.platform | tr -d '\r'
  } >"$OUT_DIR/device_identity.txt"
}

verify_artifact() {
  mkdir -p "$OUT_DIR/preflight"
  {
    printf 'artifact=%s\n' "$ARTIFACT"
    printf 'app_id=%s\n' "$APP_ID"
    printf 'activity=%s\n' "$ACTIVITY"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'marker=%s\n' "$MARKER"
    printf 'normal_ui_connected=no\n'
    printf 'selected_path_npu_normal_route=no\n'
    printf 'high_level_generate_response=no\n'
    printf 'streaming_generation=no\n'
  } >"$OUT_DIR/preflight_config.txt"

  if [ ! -d "$ARTIFACT" ]; then
    printf 'artifact_integrity=fail\nreason=artifact directory missing\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
    return 1
  fi
  {
    strings "$ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -E "$MARKER|qairt244|max_output|token" || true
    if [ -f "$ARTIFACT/metadata/litertlm_external_diff.patch" ]; then
      grep -E "$MARKER|SetMaxOutputTokens\\(3\\)" "$ARTIFACT/metadata/litertlm_external_diff.patch" || true
    fi
  } >"$OUT_DIR/preflight/custom_artifact_static_hits.txt"
  if ! grep -q "$MARKER" "$OUT_DIR/preflight/custom_artifact_static_hits.txt"; then
    printf 'artifact_integrity=fail\nreason=missing marker %s\n' "$MARKER" >"$OUT_DIR/preflight/artifact_integrity_check.txt"
    return 1
  fi
  if ! grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/preflight/custom_artifact_static_hits.txt"; then
    printf 'artifact_integrity=fail\nreason=missing SetMaxOutputTokens(3) evidence\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
    return 1
  fi
  printf 'artifact_integrity=pass\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
}

sample_memory() {
  local label="$1"
  safe_adb_shell pidof "$APP_ID" | tr -d '\r' >"$OUT_DIR/pid_${label}.txt"
  adb shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_${label}.txt" 2>&1 || true
  adb shell dumpsys meminfo >"$OUT_DIR/dumpsys_meminfo_${label}.txt" 2>&1 || true
  adb shell cat /proc/meminfo >"$OUT_DIR/proc_meminfo_${label}.txt" 2>&1 || true
}

extract_one_meminfo() {
  local label="$1"
  local file="$OUT_DIR/meminfo_${label}.txt"
  awk -v label="$label" '
    BEGIN {
      total_pss="NA"; total_private_dirty="NA";
      native_pss="NA"; native_private_dirty="NA";
      dalvik_pss="NA"; dalvik_private_dirty="NA";
      code_pss="NA"; code_private_dirty="NA";
      stack_pss="NA"; stack_private_dirty="NA";
      graphics_pss="NA"; graphics_private_dirty="NA";
      java_summary="NA"; native_summary="NA";
    }
    $1 == "Native" && $2 == "Heap" && $3 ~ /^[0-9]+$/ {
      native_pss=$3; native_private_dirty=$4;
    }
    $1 == "Dalvik" && $2 == "Heap" && $3 ~ /^[0-9]+$/ {
      dalvik_pss=$3; dalvik_private_dirty=$4;
    }
    $1 == "Code" && $2 ~ /^[0-9]+$/ {
      code_pss=$2; code_private_dirty=$3;
    }
    $1 == "Code:" && $2 ~ /^[0-9]+$/ {
      code_pss=$2;
    }
    $1 == "Stack" && $2 ~ /^[0-9]+$/ {
      stack_pss=$2; stack_private_dirty=$3;
    }
    $1 == "Stack:" && $2 ~ /^[0-9]+$/ {
      stack_pss=$2;
    }
    $1 == "Graphics" && $2 ~ /^[0-9]+$/ {
      graphics_pss=$2; graphics_private_dirty=$3;
    }
    $1 == "Graphics:" && $2 ~ /^[0-9]+$/ {
      graphics_pss=$2;
    }
    $1 == "TOTAL" && $2 ~ /^[0-9]+$/ {
      total_pss=$2; total_private_dirty=$3;
    }
    $1 == "TOTAL" && $2 == "PSS:" && $3 ~ /^[0-9]+$/ {
      total_pss=$3;
    }
    $1 == "Java" && $2 == "Heap:" && $3 ~ /^[0-9]+$/ {
      java_summary=$3;
    }
    $1 == "Native" && $2 == "Heap:" && $3 ~ /^[0-9]+$/ {
      native_summary=$3;
    }
    END {
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
        label, total_pss, total_private_dirty,
        native_pss, native_private_dirty,
        dalvik_pss, dalvik_private_dirty,
        code_pss, code_private_dirty,
        stack_pss, stack_private_dirty,
        graphics_pss, graphics_private_dirty,
        java_summary ":" native_summary;
    }
  ' "$file"
}

write_memory_summary() {
  {
    printf 'sample\ttotal_pss_kb\ttotal_private_dirty_kb\tnative_heap_pss_kb\tnative_heap_private_dirty_kb\tdalvik_heap_pss_kb\tdalvik_heap_private_dirty_kb\tcode_pss_kb\tcode_private_dirty_kb\tstack_pss_kb\tstack_private_dirty_kb\tgraphics_pss_kb\tgraphics_private_dirty_kb\tjava_heap_summary_kb:native_heap_summary_kb\n'
    for label in before after_install smoke_before after after_3s after_10s; do
      extract_one_meminfo "$label"
    done
  } >"$OUT_DIR/memory_summary.tsv"

  awk -F '\t' '
    NR == 1 { next }
    {
      total[$1]=$2; native[$1]=$4; native_dirty[$1]=$5; dalvik[$1]=$6;
    }
    END {
      print "metric\tbefore\tafter\tafter_3s\tafter_10s\tdelta_after_minus_before\tdelta_10s_minus_before";
      print_metric("total_pss_kb", total["before"], total["after"], total["after_3s"], total["after_10s"]);
      print_metric("native_heap_pss_kb", native["before"], native["after"], native["after_3s"], native["after_10s"]);
      print_metric("native_heap_private_dirty_kb", native_dirty["before"], native_dirty["after"], native_dirty["after_3s"], native_dirty["after_10s"]);
      print_metric("dalvik_heap_pss_kb", dalvik["before"], dalvik["after"], dalvik["after_3s"], dalvik["after_10s"]);
    }
    function delta(a, b) {
      if (a == "" || b == "" || a == "NA" || b == "NA") return "NA";
      return b - a;
    }
    function print_metric(name, before, after, after3, after10) {
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
        name, before, after, after3, after10, delta(before, after), delta(before, after10);
    }
  ' "$OUT_DIR/memory_summary.tsv" >"$OUT_DIR/memory_delta.tsv"
}

classify_tombstone_freshness() {
  local run_id="$1"
  local result_file="$OUT_DIR/smoke_result.txt"
  local crash_summary="$OUT_DIR/diagnostics/crash_summary.md"
  local tombstone_latest="$OUT_DIR/diagnostics/tombstone_latest.txt"
  local tombstone_app_extract="$OUT_DIR/diagnostics/tombstone_app_extract.txt"
  local dropbox_app_extract="$OUT_DIR/diagnostics/dropbox_app_extract.txt"
  local stage_file="$OUT_DIR/diagnostics/stage_file.txt"
  local note="$OUT_DIR/stale_tombstone_note.md"
  local classification="no-fresh-tombstone"
  local result_status="missing"
  local signal_line="missing"
  local tombstone_path="missing"
  local tombstone_contains_run_id="false"
  local current_run_marker_present="false"
  local process_alive="false"
  local process_line=""

  if grep -q '^result=success$' "$result_file" 2>/dev/null; then
    result_status="success"
  elif [ -s "$result_file" ]; then
    result_status="present-non-success"
  fi
  if [ -s "$crash_summary" ]; then
    signal_line="$(grep -m1 '^- signal:' "$crash_summary" 2>/dev/null | sed 's/^- signal: //')"
  fi
  if [ -s "$OUT_DIR/diagnostics/tombstone_path.txt" ]; then
    tombstone_path="$(tr -d '\r' <"$OUT_DIR/diagnostics/tombstone_path.txt")"
  fi
  if grep -Fq "$run_id" "$tombstone_latest" "$tombstone_app_extract" "$dropbox_app_extract" 2>/dev/null; then
    tombstone_contains_run_id="true"
  fi
  if grep -Fq "$run_id" "$stage_file" "$OUT_DIR/stage_file.txt" "$OUT_DIR/native_diag_tail.txt" "$OUT_DIR/smoke_result.txt" 2>/dev/null; then
    current_run_marker_present="true"
  fi

  process_line="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$process_line" ]; then
    process_alive="true"
  fi

  if printf '%s' "$signal_line" | grep -q 'SIG'; then
    if [ "$tombstone_contains_run_id" = "true" ]; then
      classification="fresh-crash"
    elif [ "$result_status" = "success" ] && [ "$current_run_marker_present" = "true" ]; then
      classification="stale-tombstone-ignored"
    else
      classification="tombstone-unmatched-review-needed"
    fi
  elif [ "$result_status" = "success" ]; then
    classification="no-fresh-tombstone"
  fi

  {
    printf '# Tombstone Freshness Classification\n\n'
    printf '%s\n' "- classification: \`$classification\`"
    printf '%s\n' "- smoke run id: \`$run_id\`"
    printf '%s\n' "- result status: \`$result_status\`"
    printf '%s\n' "- selected tombstone path: \`$tombstone_path\`"
    printf '%s\n' "- signal line: \`$signal_line\`"
    printf '%s\n' "- tombstone contains smoke run id: \`$tombstone_contains_run_id\`"
    printf '%s\n' "- current run marker present in app files: \`$current_run_marker_present\`"
    printf '%s\n' "- process alive after smoke: \`$process_alive\`"
    printf '%s\n\n' "- process pid: \`${process_line:-missing}\`"
    if [ "$classification" = "stale-tombstone-ignored" ]; then
      printf 'The collector selected an older tombstone that does not contain the current smoke run id. Because the smoke result is success and current-run markers are present in app-private files, this tombstone is ignored for the smoke outcome.\n'
    elif [ "$classification" = "fresh-crash" ]; then
      printf 'The selected tombstone contains the current smoke run id and is classified as a fresh crash.\n'
    else
      printf 'No fresh crash evidence was found for this smoke run.\n'
    fi
  } >"$note"
  cp "$note" "$OUT_DIR/diagnostics/stale_tombstone_note.md" 2>/dev/null || true
  printf '%s\n' "$classification" >"$OUT_DIR/tombstone_classification.txt"
}

collect_smoke_outputs() {
  local run_id="$1"
  adb shell run-as "$APP_ID" cat "files/qairt244_short_multitoken_smoke_result.txt" >"$OUT_DIR/smoke_result.txt" 2>"$OUT_DIR/smoke_result.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/qairt244_native_diag.txt" >"$OUT_DIR/native_diag.txt" 2>"$OUT_DIR/native_diag.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/npu_engine_initialize_last_stage.txt" >"$OUT_DIR/stage_file.txt" 2>"$OUT_DIR/stage_file.pull.err" || true
  tail -220 "$OUT_DIR/native_diag.txt" >"$OUT_DIR/native_diag_tail.txt" 2>/dev/null || true
  adb logcat -d -t 500 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
      --app-id "$APP_ID" \
      --label customnpu-memory-profile \
      --run-id "$run_id" \
      --output-dir "$OUT_DIR/diagnostics" \
      >"$OUT_DIR/diagnostics_collect.log" 2>&1 || true
  fi
  classify_tombstone_freshness "$run_id"

  cat >"$OUT_DIR/smoke_summary.md" <<EOF
# Isolated Short Multi-Token Smoke Embedded In Memory Profile

\`\`\`text
$(cat "$OUT_DIR/smoke_result.txt" 2>/dev/null)
\`\`\`
EOF
}

write_summary() {
  local result output elapsed decode cleanup tombstone npu_evidence
  result="$(grep -m1 '^result=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  output="$(grep -m1 '^output=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  elapsed="$(grep -m1 '^elapsed_ms=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  decode="$(grep -m1 '^decode_elapsed_ms=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  cleanup="$(grep -m1 '^cleanup_elapsed_ms=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  npu_evidence="$(grep -m1 '^npu_backend_evidence=' "$OUT_DIR/smoke_result.txt" 2>/dev/null | cut -d= -f2-)"
  tombstone="$(cat "$OUT_DIR/tombstone_classification.txt" 2>/dev/null || true)"

  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 NPU Memory Cleanup Profile

Artifact: \`$OUT_DIR\`

## Smoke Outcome

\`\`\`text
result=${result:-none}
output=${output:-none}
prompt=$PROMPT
max_output_tokens=$MAX_OUTPUT_TOKENS
elapsed_ms=${elapsed:-none}
decode_elapsed_ms=${decode:-none}
cleanup_elapsed_ms=${cleanup:-none}
npu_backend_evidence=${npu_evidence:-none}
tombstone_classification=${tombstone:-none}
\`\`\`

## Memory Samples

Samples captured:

- before install / launch
- after install
- smoke before Activity launch
- immediately after smoke result file appeared
- 3 seconds after smoke
- 10 seconds after smoke

See \`memory_summary.tsv\` and \`memory_delta.tsv\`.

## Cleanup Evidence

Native diagnostics are summarized in \`native_diag_tail.txt\`. This first memory
profile is a baseline only; retained PSS from mapped QAIRT/QNN libraries or a
still-alive process is not treated as a leak by itself.

## Safety

This script used only the isolated short multi-token smoke Activity. It did not
connect NPU to the normal UI, did not set \`selectedPath=npu\` on the normal
route, did not call high-level \`generateResponse\`, and did not use streaming.
EOF
}

run_profile() {
  local run_id waited
  run_id="$(date +%s%3N)"
  waited=0
  sample_memory before

  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$ARTIFACT" >"$OUT_DIR/stage_custom_build.log" 2>&1
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/assemble.log" 2>&1
  adb install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/install.log" 2>&1

  sample_memory after_install
  adb logcat -c >/dev/null 2>&1 || true
  adb shell run-as "$APP_ID" rm -f \
    "files/qairt244_short_multitoken_smoke_result.txt" \
    "files/qairt244_native_diag.txt" \
    "files/npu_engine_initialize_last_stage.txt" >/dev/null 2>&1 || true
  sample_memory smoke_before

  {
    printf 'run_id=%s\n' "$run_id"
    printf 'app_id=%s\n' "$APP_ID"
    printf 'activity=%s\n' "$ACTIVITY"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'marker=%s\n' "$MARKER"
    printf 'artifact=%s\n' "$ARTIFACT"
  } >"$OUT_DIR/run_metadata.txt"

  adb shell am start -n "$APP_ID/$ACTIVITY" \
    --ez runShortMultitokenSmoke true \
    --es model_path "$MODEL_PATH" \
    --es run_id "$run_id" >"$OUT_DIR/am_start.txt" 2>&1 || true

  while [ "$waited" -lt "$TIMEOUT_SECONDS" ]; do
    if adb shell run-as "$APP_ID" test -f "files/qairt244_short_multitoken_smoke_result.txt" >/dev/null 2>&1; then
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
    printf 'timeout=true\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/timeout_state.txt"
    adb shell am force-stop "$APP_ID" >>"$OUT_DIR/timeout_state.txt" 2>&1 || true
  else
    printf 'timeout=false\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/timeout_state.txt"
  fi

  sample_memory after
  sleep 3
  sample_memory after_3s
  sleep 7
  sample_memory after_10s

  collect_smoke_outputs "$run_id"
  write_memory_summary
  write_summary
}

write_policy
verify_device || {
  write_summary
  log "blocked: no eligible device"
  log "artifact: $OUT_DIR"
  exit 1
}
verify_artifact || {
  write_summary
  log "blocked: artifact preflight failed"
  log "artifact: $OUT_DIR"
  exit 1
}
run_profile
log "artifact: $OUT_DIR"
