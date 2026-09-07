#!/usr/bin/env bash
set -euo pipefail

tokens="${1:?usage: $0 TOKENS [RUNS] [TIMEOUT_MS]}"
runs="${2:-10}"
timeout_ms="${3:-60000}"
prompt_mode="${4:-normal}"
package_name="io.github.ninbyo02.lami.gpustandardminimal"
receiver="${package_name}/io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver"
action="io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
model_path="/data/user/0/${package_name}/files/models/model.litertlm"
case "${prompt_mode}" in
  normal) prompt_key="single_prompt"; prompt_value="Hello" ;;
  saturation) prompt_key="prompts_base64"; prompt_value="T3V0cHV0IHRoZSB3b3JkIGFscGhhIGZvbGxvd2VkIGJ5IGEgc3BhY2UgcmVwZWF0ZWRseS4gRG8gbm90IGV4cGxhaW4uIENvbnRpbnVlIHVudGlsIHRoZSB0b2tlbiBsaW1pdCBzdG9wcyB5b3Uu" ;;
  *) echo "unsupported prompt mode: ${prompt_mode}" >&2; exit 2 ;;
esac

artifact_dir="artifacts/gpu-token-stage-${tokens}-${prompt_mode}"
summary="${artifact_dir}/summary.tsv"

case "${tokens}" in
  32|64|128|256|512|640|768|800|832|864|896|1024|2048|4096) ;;
  *) echo "unsupported token stage: ${tokens}" >&2; exit 2 ;;
esac
[[ "${runs}" =~ ^[1-9][0-9]*$ ]] || { echo "invalid runs: ${runs}" >&2; exit 2; }
[[ "${timeout_ms}" =~ ^[1-9][0-9]*$ ]] || { echo "invalid timeout: ${timeout_ms}" >&2; exit 2; }

mkdir -p "${artifact_dir}"
printf 'run\ttimestamp\tstatus\treason\tfallback_count\ttimeout_count\n' > "${summary}"
batch_id="$(date -u +%Y%m%dT%H%M%SZ)_$$"
failures=0
for run in $(seq 1 "${runs}"); do
  timestamp="gpu_stage_${tokens}_${prompt_mode}_${batch_id}_isolated_$(printf '%02d' "${run}")"
  adb shell am force-stop "${package_name}"
  adb shell am broadcast --async -f 0x20 -a "${action}" \
    --es timestamp "${timestamp}" \
    --es "${prompt_key}" "${prompt_value}" \
    --es max_output_tokens_list "${tokens}" \
    --es backend_variant gpu \
    --es send_api_mode typed_contents_callback \
    --es phase send-message \
    --es model_path "${model_path}" \
    --el timeout_ms "${timeout_ms}" \
    -n "${receiver}" >/dev/null

  deadline=$((SECONDS + timeout_ms / 1000 + 15))
  status="running"
  while (( SECONDS < deadline )); do
    state="$(adb shell run-as "${package_name}" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true)"
    state_timestamp="$(sed -n 's/^timestamp=//p' <<<"${state}" | head -1 | tr -d '\r')"
    status="$(sed -n 's/^status=//p' <<<"${state}" | head -1 | tr -d '\r')"
    [[ "${state_timestamp}" == "${timestamp}" && "${status}" != "running" ]] && break
    sleep 2
  done
  reason="$(sed -n 's/^reason=//p' <<<"${state}" | head -1 | tr -d '\r')"
  fallback_count="$(sed -n 's/^fallback_count=//p' <<<"${state}" | head -1 | tr -d '\r')"
  timeout_count="$(sed -n 's/^timeout_count=//p' <<<"${state}" | head -1 | tr -d '\r')"
  csv_name="$(sed -n 's/^csv_file=//p' <<<"${state}" | head -1 | tr -d '\r')"
  printf '%s' "${state}" > "${artifact_dir}/${timestamp}.state.txt"
  adb exec-out run-as "${package_name}" cat files/litert_lm_gpu_benchmark_checkpoint.txt \
    > "${artifact_dir}/${timestamp}.checkpoint.txt" 2>/dev/null || true
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${run}" "${timestamp}" "${status}" "${reason}" "${fallback_count:-unknown}" "${timeout_count:-unknown}" \
    | tee -a "${summary}"

  if [[ -n "${csv_name}" ]]; then
    adb exec-out run-as "${package_name}" cat "files/${csv_name}" > "${artifact_dir}/${csv_name}"
  fi
  if [[ "${status}" != "success" || "${fallback_count:-1}" != "0" || "${timeout_count:-1}" != "0" ]]; then
    failures=$((failures + 1))
    echo "stopping_after_first_failure run=${run} status=${status}"
    break
  fi
done

adb shell am force-stop "${package_name}"
echo "summary=${summary} failures=${failures}/${runs}"
(( failures == 0 ))
