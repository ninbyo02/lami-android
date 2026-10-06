#!/usr/bin/env bash
set -euo pipefail
profile_root=$(realpath -m -- "${1:?Usage: build_profile_runtime.sh ROOT EXECUTORCH_PYTHON [JOBS]}")
profile_python=${2:?Supply absolute Python path from the existing ExecuTorch 1.4 environment}
[[ "$profile_python" == /* && -x "$profile_python" ]] || { echo 'Python must be an absolute executable path' >&2; exit 2; }
profile_jobs=${3:-4}
[[ "$profile_jobs" =~ ^[1-9][0-9]*$ ]] || { echo 'JOBS must be positive' >&2; exit 2; }
profile_source="$profile_root/executorch"
mkdir -p "$profile_root"
if [[ ! -e "$profile_source" ]]; then
  git clone --depth 1 --branch v1.4.0 https://github.com/pytorch/executorch.git "$profile_source"
fi
[[ $(git -C "$profile_source" rev-parse HEAD) == 3dd7ccd1d863fad22639dd2d918ae34a41ce45f0 ]] || { echo 'Expected pinned ExecuTorch v1.4.0 source' >&2; exit 2; }
git -C "$profile_source" diff --quiet
git -C "$profile_source" diff --cached --quiet
git -C "$profile_source" submodule update --init --depth 1 --jobs "$profile_jobs" \
  backends/xnnpack/third-party/FP16 backends/xnnpack/third-party/FXdiv \
  backends/xnnpack/third-party/XNNPACK backends/xnnpack/third-party/cpuinfo \
  backends/xnnpack/third-party/pthreadpool kernels/optimized/third-party/eigen \
  third-party/flatbuffers third-party/flatcc third-party/gflags \
  third-party/pocketfft third-party/json
cmake -S "$profile_source" -B "$profile_root/build" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DPYTHON_EXECUTABLE="$profile_python" \
  -DPython3_EXECUTABLE="$profile_python" -DEXECUTORCH_BUILD_XNNPACK=ON \
  -DEXECUTORCH_BUILD_EXECUTOR_RUNNER=ON -DEXECUTORCH_ENABLE_EVENT_TRACER=ON \
  -DEXECUTORCH_BUILD_DEVTOOLS=ON -DEXECUTORCH_BUILD_EXTENSION_RUNNER_UTIL=ON \
  -DEXECUTORCH_BUILD_EXTENSION_EVALUE_UTIL=ON -DEXECUTORCH_BUILD_KERNELS_OPTIMIZED=ON \
  -DEXECUTORCH_BUILD_KERNELS_QUANTIZED=ON -DEXECUTORCH_BUILD_EXTENSION_DATA_LOADER=ON
cmake --build "$profile_root/build" --target executor_runner -j "$profile_jobs"
