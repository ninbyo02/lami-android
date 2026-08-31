#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
scenario="$root_dir/scripts/data/conversation_api_ab_v1.json"
summarizer="$root_dir/scripts/summarize_conversation_api_ab.py"
endpoint=
gpu_apk=
npu_apk=
gpu_app_id=io.github.ninbyo02.lami
npu_app_id=io.github.ninbyo02.lami.npuvalidation
repetitions=1
timeout_seconds=150
skip_install=false
require_quality_pass=false
gpu_model_path=

while (($#)); do
  case "$1" in
    --endpoint) endpoint=${2:?missing endpoint}; shift 2 ;;
    --gpu-apk) gpu_apk=${2:?missing GPU APK}; shift 2 ;;
    --npu-apk) npu_apk=${2:?missing NPU APK}; shift 2 ;;
    --gpu-app-id) gpu_app_id=${2:?missing GPU app id}; shift 2 ;;
    --npu-app-id) npu_app_id=${2:?missing NPU app id}; shift 2 ;;
    --scenario) scenario=${2:?missing scenario}; shift 2 ;;
    --repetitions) repetitions=${2:?missing repetitions}; shift 2 ;;
    --timeout) timeout_seconds=${2:?missing timeout}; shift 2 ;;
    --gpu-model-path) gpu_model_path=${2:?missing GPU model path}; shift 2 ;;
    --skip-install) skip_install=true; shift ;;
    --require-quality-pass) require_quality_pass=true; shift ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_conversation_api_ab.sh \
    --endpoint HOST:PORT \
    --gpu-apk PATH \
    --npu-apk PATH \
    [--repetitions N] \
    [--timeout SECONDS] \
    [--gpu-model-path APP_PRIVATE_PATH] \
    [--skip-install] \
    [--require-quality-pass]

Runs the same ordered prompts in one LiteRT-LM Conversation on GPU and NPU.
The production NPU route, database, TTS, and ChatScreen are not invoked.

The report separates:
  transport_result  Conversation/API execution and nonblank sanitized output
  quality_result    scenario answer checks

GPU's Kotlin Conversation API does not expose per-send max_output_tokens in
the current 0.11.0 surface. The requested value is recorded, while the
effective GPU value remains unavailable. NPU records the C++ OptionalArgs cap.
EOF
      exit 0
      ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$endpoint" ]] || {
  echo "--endpoint is required" >&2
  exit 2
}
[[ -f "$scenario" ]] || {
  echo "scenario not found: $scenario" >&2
  exit 2
}
[[ -f "$summarizer" ]] || {
  echo "summarizer not found: $summarizer" >&2
  exit 2
}
[[ "$repetitions" =~ ^[1-9][0-9]*$ ]] || {
  echo "--repetitions must be a positive integer" >&2
  exit 2
}
((repetitions <= 20)) || {
  echo "--repetitions must be 20 or less" >&2
  exit 2
}
[[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]] || {
  echo "--timeout must be a positive integer" >&2
  exit 2
}
if [[ "$skip_install" == false ]]; then
  [[ -f "$gpu_apk" ]] || {
    echo "GPU APK not found: $gpu_apk" >&2
    exit 2
  }
  [[ -f "$npu_apk" ]] || {
    echo "NPU APK not found: $npu_apk" >&2
    exit 2
  }
fi

timestamp=$(date +%Y%m%d_%H%M%S)
out_dir="$root_dir/artifacts/conversation_api_ab/$timestamp"
mkdir -p "$out_dir/gpu" "$out_dir/npu"
result_file=files/conversation_ab_benchmark_result.json
gpu_action=io.github.ninbyo02.lami.action.CONVERSATION_AB_GPU
gpu_receiver=io.github.ninbyo02.lami.gpu.ConversationAbGpuReceiver
npu_action=io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION
npu_receiver=io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver

adb_cmd() {
  command adb -s "$endpoint" "$@"
}

scenario_value() {
  local expression=$1
  python3 -c "import json; d=json.load(open('$scenario', encoding='utf-8')); print($expression)"
}

scenario_id=$(scenario_value "d['scenario_id']")
max_output_tokens=$(scenario_value "d['requested_max_output_tokens']")
prompts_b64=$(
  python3 -c \
    "import base64,json; d=json.load(open('$scenario', encoding='utf-8')); p=[x['prompt'] for x in d['turns']]; print(base64.b64encode(json.dumps(p, ensure_ascii=False, separators=(',', ':')).encode()).decode())"
)
gpu_model_path_b64=
if [[ -n "$gpu_model_path" ]]; then
  gpu_model_path_b64=$(printf '%s' "$gpu_model_path" | base64 | tr -d '\n')
fi

adb_cmd connect "$endpoint" >"$out_dir/adb_connect.txt"
adb_cmd get-state | grep -Fxq device
adb_cmd shell getprop ro.product.model >"$out_dir/device_model.txt"
adb_cmd shell getprop ro.build.fingerprint >"$out_dir/device_fingerprint.txt"

if [[ "$skip_install" == false ]]; then
  adb_cmd install -r -t --no-streaming "$gpu_apk" >"$out_dir/gpu_install.txt"
  adb_cmd install -r -t --no-streaming "$npu_apk" >"$out_dir/npu_install.txt"
fi

unstop_package() {
  local app_id=$1
  adb_cmd shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1 \
    >/dev/null 2>&1 || true
  adb_cmd shell am start \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME \
    >/dev/null 2>&1 || true
}

wait_for_result() {
  local app_id=$1
  local destination=$2
  local deadline=$((SECONDS + timeout_seconds))
  while ((SECONDS < deadline)); do
    if adb_cmd exec-out run-as "$app_id" cat "$result_file" \
      >"$destination.tmp" 2>/dev/null; then
      if python3 -c \
        "import json; d=json.load(open('$destination.tmp', encoding='utf-8')); assert d.get('scenarioId') == '$scenario_id'; assert d.get('status') in ('success','failure')" \
        >/dev/null 2>&1; then
        mv "$destination.tmp" "$destination"
        return 0
      fi
    fi
    sleep 1
  done
  rm -f "$destination.tmp"
  return 1
}

run_gpu() {
  local run_index=$1
  local destination="$out_dir/gpu/run_$run_index.json"
  unstop_package "$gpu_app_id"
  adb_cmd shell run-as "$gpu_app_id" rm -f "$result_file" || true
  adb_cmd logcat -c
  local args=(
    shell am broadcast --include-stopped-packages
    -a "$gpu_action"
    -n "$gpu_app_id/$gpu_receiver"
    --es scenario_id "$scenario_id"
    --es conversation_prompts_base64 "$prompts_b64"
    --ei max_output_tokens "$max_output_tokens"
    --el timeout_ms "$((timeout_seconds * 1000))"
  )
  if [[ -n "$gpu_model_path_b64" ]]; then
    args+=(--es model_path_base64 "$gpu_model_path_b64")
  fi
  adb_cmd "${args[@]}" >"$out_dir/gpu/run_$run_index.broadcast.txt"
  if ! wait_for_result "$gpu_app_id" "$destination"; then
    adb_cmd logcat -b all -d -t 4000 \
      >"$out_dir/gpu/run_$run_index.timeout.logcat.txt" 2>/dev/null || true
    echo "GPU run $run_index timed out" >&2
    return 1
  fi
}

run_npu() {
  local run_index=$1
  local destination="$out_dir/npu/run_$run_index.json"
  unstop_package "$npu_app_id"
  adb_cmd shell run-as "$npu_app_id" rm -f "$result_file" || true
  adb_cmd shell run-as "$npu_app_id" rm -f \
    files/dev_only_npu_one_turn_conversation_result.txt \
    files/qairt244_conversation_api_probe_result.txt \
    files/qairt244_conversation_api_probe_diag.txt || true
  adb_cmd logcat -c
  adb_cmd shell am broadcast --include-stopped-packages \
    -a "$npu_action" \
    -n "$npu_app_id/$npu_receiver" \
    --es native_probe_mode conversation_api \
    --es scenario_id "$scenario_id" \
    --es conversation_prompts_base64 "$prompts_b64" \
    --ei max_output_tokens "$max_output_tokens" \
    >"$out_dir/npu/run_$run_index.broadcast.txt"
  if ! wait_for_result "$npu_app_id" "$destination"; then
    adb_cmd logcat -b all -d -t 4000 \
      >"$out_dir/npu/run_$run_index.timeout.logcat.txt" 2>/dev/null || true
    echo "NPU run $run_index timed out" >&2
    return 1
  fi
}

failure=0
for run_index in $(seq 1 "$repetitions"); do
  run_gpu "$run_index" || failure=1
  run_npu "$run_index" || failure=1
done

gpu_results=()
npu_results=()
for result_path in "$out_dir"/gpu/run_*.json; do
  [[ -f "$result_path" ]] && gpu_results+=(--gpu "$result_path")
done
for result_path in "$out_dir"/npu/run_*.json; do
  [[ -f "$result_path" ]] && npu_results+=(--npu "$result_path")
done
((${#gpu_results[@]} == repetitions * 2)) || failure=1
((${#npu_results[@]} == repetitions * 2)) || failure=1
((failure == 0)) || {
  echo "A/B transport evidence incomplete: $out_dir" >&2
  exit 2
}

summary_args=(
  --scenario "$scenario"
  "${gpu_results[@]}"
  "${npu_results[@]}"
  --output-dir "$out_dir/report"
)
if [[ "$require_quality_pass" == true ]]; then
  summary_args+=(--require-quality-pass)
fi
python3 "$summarizer" "${summary_args[@]}"
echo "artifact=$out_dir"
