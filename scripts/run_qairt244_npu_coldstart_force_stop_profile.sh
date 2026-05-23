#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ARTIFACT="artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=3
TIMEOUT_SECONDS=30
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_coldstart_force_stop_profile/$TIMESTAMP"

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact)
      ARTIFACT="${2:-}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:-30}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_coldstart_force_stop_profile.sh [--artifact <custom-build-artifact>] [--timeout <seconds>]

Force-stops the customBuildExperimentDebug app, captures cold-start package
meminfo, runs exactly one isolated QAIRT 3-token smoke through
run_qairt244_short_multitoken_smoke.sh, captures post-run memory, force-stops
again, and captures 3s/10s force-stop cleanup memory.

This does not connect NPU to the normal UI, does not set selectedPath=npu on the
normal route, does not call high-level generateResponse, and does not use
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
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-npu-coldstart-force-stop] %s\n' "$*"
}

clean_file() {
  local file="$1"
  [ -f "$file" ] || return 0
  sed -i 's/[[:space:]]*$//' "$file"
  sed -i '${/^$/d;}' "$file"
}

safe_adb_shell() {
  adb shell "$@" 2>&1 || true
}

write_policy() {
  cat >"$OUT_DIR/large_artifacts_local_only.md" <<'EOF'
# Large Artifacts Are Local-Only

This cold-start force-stop profile references local QAIRT/LiteRT artifacts and
the nested smoke artifact. Rebuilt `.so` files, APKs, and extracted native
libraries remain local-only. Commit text summaries, meminfo, run metadata, and
diagnostics only.
EOF
}

verify_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  clean_file "$OUT_DIR/adb_devices.txt"
  local device_line
  device_line="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ {print $1; exit}' "$OUT_DIR/adb_devices.txt")"
  if [ -z "$device_line" ]; then
    printf 'result=blocked\nreason=no-nubia-or-non-emulator-adb-device\n' >"$OUT_DIR/result.txt"
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
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
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
    strings "$ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -E 'qairt244_short_multitoken_smoke_v1|qairt244|max_output|token' || true
    if [ -f "$ARTIFACT/metadata/litertlm_external_diff.patch" ]; then
      grep -E 'qairt244_short_multitoken_smoke_v1|SetMaxOutputTokens\(3\)' "$ARTIFACT/metadata/litertlm_external_diff.patch" || true
    fi
  } >"$OUT_DIR/preflight/custom_artifact_static_hits.txt"
  if ! grep -q 'qairt244_short_multitoken_smoke_v1' "$OUT_DIR/preflight/custom_artifact_static_hits.txt"; then
    printf 'artifact_integrity=fail\nreason=missing short multitoken marker\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
    return 1
  fi
  if ! grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/preflight/custom_artifact_static_hits.txt"; then
    printf 'artifact_integrity=fail\nreason=missing SetMaxOutputTokens(3) evidence\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
    return 1
  fi
  printf 'artifact_integrity=pass\n' >"$OUT_DIR/preflight/artifact_integrity_check.txt"
}

sample_package_memory() {
  local label="$1"
  safe_adb_shell pidof "$APP_ID" | tr -d '\r' >"$OUT_DIR/pid_${label}.txt"
  adb shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_${label}.txt" 2>&1 || true
  adb shell cat /proc/meminfo >"$OUT_DIR/proc_meminfo_${label}.txt" 2>&1 || true
  clean_file "$OUT_DIR/meminfo_${label}.txt"
  clean_file "$OUT_DIR/proc_meminfo_${label}.txt"
}

extract_one_meminfo() {
  local label="$1"
  local file="$OUT_DIR/meminfo_${label}.txt"
  awk -v label="$label" '
    BEGIN {
      total_pss="NA"; total_private_dirty="NA";
      native_pss="NA"; native_private_dirty="NA";
      dalvik_pss="NA"; dalvik_private_dirty="NA";
      code_pss="NA"; stack_pss="NA"; graphics_pss="NA";
      no_process="false";
    }
    /No process/ || /No .*process.*found/ { no_process="true"; }
    $1 == "Native" && $2 == "Heap" && $3 ~ /^[0-9]+$/ { native_pss=$3; native_private_dirty=$4; }
    $1 == "Dalvik" && $2 == "Heap" && $3 ~ /^[0-9]+$/ { dalvik_pss=$3; dalvik_private_dirty=$4; }
    $1 == "TOTAL" && $2 ~ /^[0-9]+$/ { total_pss=$2; total_private_dirty=$3; }
    $1 == "TOTAL" && $2 == "PSS:" && $3 ~ /^[0-9]+$/ { total_pss=$3; }
    $1 == "Code:" && $2 ~ /^[0-9]+$/ { code_pss=$2; }
    $1 == "Stack:" && $2 ~ /^[0-9]+$/ { stack_pss=$2; }
    $1 == "Graphics:" && $2 ~ /^[0-9]+$/ { graphics_pss=$2; }
    END {
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
        label, total_pss, total_private_dirty,
        native_pss, native_private_dirty,
        dalvik_pss, dalvik_private_dirty,
        code_pss, stack_pss, graphics_pss, no_process;
    }
  ' "$file"
}

write_memory_tables() {
  {
    printf 'sample\ttotal_pss_kb\ttotal_private_dirty_kb\tnative_heap_pss_kb\tnative_heap_private_dirty_kb\tdalvik_heap_pss_kb\tdalvik_heap_private_dirty_kb\tcode_pss_kb\tstack_pss_kb\tgraphics_pss_kb\tmeminfo_no_process\n'
    for label in after_force_stop after_smoke after_3s after_final_force_stop_3s after_final_force_stop_10s; do
      extract_one_meminfo "$label"
    done
  } >"$OUT_DIR/memory_summary.tsv"

  awk -F '\t' '
    NR == 1 { next }
    {
      total[$1]=$2; native[$1]=$4; dalvik[$1]=$6; no_process[$1]=$11;
    }
    END {
      print "metric\tafter_smoke\tafter_3s\tafter_final_force_stop_3s\tafter_final_force_stop_10s";
      print "total_pss_kb\t" total["after_smoke"] "\t" total["after_3s"] "\t" total["after_final_force_stop_3s"] "\t" total["after_final_force_stop_10s"];
      print "native_heap_pss_kb\t" native["after_smoke"] "\t" native["after_3s"] "\t" native["after_final_force_stop_3s"] "\t" native["after_final_force_stop_10s"];
      print "dalvik_heap_pss_kb\t" dalvik["after_smoke"] "\t" dalvik["after_3s"] "\t" dalvik["after_final_force_stop_3s"] "\t" dalvik["after_final_force_stop_10s"];
      print "meminfo_no_process\t" no_process["after_smoke"] "\t" no_process["after_3s"] "\t" no_process["after_final_force_stop_3s"] "\t" no_process["after_final_force_stop_10s"];
    }
  ' "$OUT_DIR/memory_summary.tsv" >"$OUT_DIR/memory_delta.tsv"
}

copy_smoke_outputs() {
  local smoke_dir="$1"
  cp "$smoke_dir/summary.md" "$OUT_DIR/smoke_summary.md" 2>/dev/null || true
  cp "$smoke_dir/result.txt" "$OUT_DIR/smoke_result.txt" 2>/dev/null || true
  cp "$smoke_dir/stale_tombstone_note.md" "$OUT_DIR/stale_tombstone_note.md" 2>/dev/null || true
  cp "$smoke_dir/tombstone_classification.txt" "$OUT_DIR/tombstone_classification.txt" 2>/dev/null || true
  cp "$smoke_dir/diagnostics/native_lib_build_ids.txt" "$OUT_DIR/native_lib_build_ids.txt" 2>/dev/null || true
  tail -220 "$smoke_dir/native_diag.txt" >"$OUT_DIR/native_diag_tail.txt" 2>/dev/null || true
  adb logcat -d -t 500 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  printf '%s\n' "$smoke_dir" >"$OUT_DIR/smoke_artifact_path.txt"
}

field() {
  local key="$1"
  local file="$2"
  grep -m1 "^$key=" "$file" 2>/dev/null | cut -d= -f2-
}

mem_value() {
  local sample="$1"
  local col="$2"
  awk -F '\t' -v sample="$sample" -v col="$col" 'NR == 1 { for (i = 1; i <= NF; i++) ix[$i] = i; next } $1 == sample { print $ix[col]; exit }' "$OUT_DIR/memory_summary.tsv"
}

write_summary() {
  local result output elapsed prefill decode cleanup tombstone
  local pid_before pid_after_force pid_final_3 pid_final_10
  local after_total after_native after3_total after3_native final3_no_process final10_no_process
  result="$(field result "$OUT_DIR/smoke_result.txt")"
  output="$(field output "$OUT_DIR/smoke_result.txt")"
  elapsed="$(field elapsed_ms "$OUT_DIR/smoke_result.txt")"
  prefill="$(field prefill_elapsed_ms "$OUT_DIR/smoke_result.txt")"
  decode="$(field decode_elapsed_ms "$OUT_DIR/smoke_result.txt")"
  cleanup="$(field cleanup_elapsed_ms "$OUT_DIR/smoke_result.txt")"
  tombstone="$(cat "$OUT_DIR/tombstone_classification.txt" 2>/dev/null || true)"
  pid_before="$(tr -d '\r\n' <"$OUT_DIR/pid_before_force_stop.txt" 2>/dev/null || true)"
  pid_after_force="$(tr -d '\r\n' <"$OUT_DIR/pid_after_force_stop.txt" 2>/dev/null || true)"
  pid_final_3="$(tr -d '\r\n' <"$OUT_DIR/pid_after_final_force_stop_3s.txt" 2>/dev/null || true)"
  pid_final_10="$(tr -d '\r\n' <"$OUT_DIR/pid_after_final_force_stop_10s.txt" 2>/dev/null || true)"
  after_total="$(mem_value after_smoke total_pss_kb)"
  after_native="$(mem_value after_smoke native_heap_pss_kb)"
  after3_total="$(mem_value after_3s total_pss_kb)"
  after3_native="$(mem_value after_3s native_heap_pss_kb)"
  final3_no_process="$(mem_value after_final_force_stop_3s meminfo_no_process)"
  final10_no_process="$(mem_value after_final_force_stop_10s meminfo_no_process)"

  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 NPU Cold-Start Force-Stop Memory Profile

Artifact: \`$OUT_DIR\`

## Smoke Outcome

\`\`\`text
result=${result:-none}
output=${output:-none}
prompt=$PROMPT
max_output_tokens=$MAX_OUTPUT_TOKENS
elapsed_ms=${elapsed:-none}
prefill_elapsed_ms=${prefill:-none}
decode_elapsed_ms=${decode:-none}
cleanup_elapsed_ms=${cleanup:-none}
tombstone_classification=${tombstone:-none}
\`\`\`

## Cold-Start / Force-Stop State

\`\`\`text
pid_before_force_stop=${pid_before:-none}
pid_after_force_stop=${pid_after_force:-none}
pid_after_final_force_stop_3s=${pid_final_3:-none}
pid_after_final_force_stop_10s=${pid_final_10:-none}
meminfo_after_final_force_stop_3s_no_process=${final3_no_process:-none}
meminfo_after_final_force_stop_10s_no_process=${final10_no_process:-none}
cold_start=$([ -z "$pid_after_force" ] && printf true || printf false)
force_stop_cleanup=$([ -z "$pid_final_3" ] && [ -z "$pid_final_10" ] && [ "$final3_no_process" = "true" ] && [ "$final10_no_process" = "true" ] && printf pass || printf review)
leak_classification=$([ -z "$pid_final_3" ] && [ -z "$pid_final_10" ] && [ "$final3_no_process" = "true" ] && [ "$final10_no_process" = "true" ] && printf no_app_process_retained_after_force_stop || printf review_needed)
\`\`\`

The smoke launch was cold-started when \`pid_after_force_stop\` was empty.

## Memory Summary

\`\`\`text
after_smoke_total_pss_kb=${after_total:-NA}
after_smoke_native_heap_pss_kb=${after_native:-NA}
after_3s_total_pss_kb=${after3_total:-NA}
after_3s_native_heap_pss_kb=${after3_native:-NA}
after_final_force_stop_3s_total_pss_kb=$(mem_value after_final_force_stop_3s total_pss_kb)
after_final_force_stop_10s_total_pss_kb=$(mem_value after_final_force_stop_10s total_pss_kb)
\`\`\`

See \`memory_summary.tsv\` and \`memory_delta.tsv\`.

## Cleanup Interpretation

If final force-stop samples show no pid and meminfo reports no process, app PSS
is considered reclaimed for this one-run baseline. This does not prove absence
of all native leaks across repeated runs, but it is the expected cleanup
boundary for the diagnostic process.

## Safety

This wrapper called the existing isolated short multi-token smoke exactly once.
It did not connect NPU to the normal UI, did not set \`selectedPath=npu\`, did
not call high-level \`generateResponse\`, did not stream, and did not exceed
\`maxOutputTokens=3\`.
EOF
}

run_profile() {
  local smoke_dir
  sample_package_memory before_force_stop
  cp "$OUT_DIR/pid_before_force_stop.txt" "$OUT_DIR/pid_before_force_stop_initial.txt" 2>/dev/null || true
  adb shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_before.txt" 2>&1 || true
  sleep 1
  sample_package_memory after_force_stop
  cp "$OUT_DIR/proc_meminfo_after_force_stop.txt" "$OUT_DIR/proc_meminfo_before.txt" 2>/dev/null || true

  bash scripts/run_qairt244_short_multitoken_smoke.sh \
    --artifact "$ARTIFACT" \
    --run \
    --timeout "$TIMEOUT_SECONDS" >"$OUT_DIR/smoke_runner.log" 2>&1 || true
  smoke_dir="$(ls -td artifacts/qairt244_short_multitoken_smoke/* 2>/dev/null | head -1)"
  printf '%s\n' "$smoke_dir" >"$OUT_DIR/smoke_artifact_path.txt"

  sample_package_memory after_smoke
  sleep 3
  sample_package_memory after_3s

  adb shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_after.txt" 2>&1 || true
  sleep 3
  sample_package_memory after_final_force_stop_3s
  sleep 7
  sample_package_memory after_final_force_stop_10s
  cp "$OUT_DIR/proc_meminfo_after_final_force_stop_10s.txt" "$OUT_DIR/proc_meminfo_after.txt" 2>/dev/null || true

  copy_smoke_outputs "$smoke_dir"
  write_memory_tables
  write_summary
}

write_policy
verify_device || {
  log "blocked: no eligible device"
  exit 1
}
verify_artifact || {
  log "blocked: artifact preflight failed"
  exit 1
}
run_profile
log "artifact: $OUT_DIR"
