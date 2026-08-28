#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
runner="$root_dir/scripts/run_standard_npu_release_automated_conversation_suite.sh"
receiver="$root_dir/app/src/standardNpuRuntime/java/io/github/ninbyo02/lami/ui/screens/home/StandardNpuReleasePreflightReceiver.kt"

bash -n "$runner"
grep -Fq "capital/turn2" "$runner"
grep -Fq "name_memory/turn2" "$runner"
grep -Fq "correction/turn3" "$runner"
grep -Fq "'東京' exact" "$runner"
grep -Fq "'日本' exact" "$runner"
grep -Fq "'青葉' normalized_word" "$runner"
grep -Fq "'青' normalized_word" "$runner"
grep -Fq "prompt_input_code_points" "$runner"
grep -Fq "sampler_backend=NPU" "$runner"
grep -Fq "QNN_HTP_V79_FastRPC_native_diag" "$runner"
grep -Fq "RealNpuStandardRouteS1Provider.request(" "$receiver"
if grep -Fq "request = NpuStandardRouteNativeRequest(" "$receiver"; then
  echo "release validation receiver bypasses the production request boundary" >&2
  exit 1
fi
echo "standard_release_automated_conversation_suite=verified"
