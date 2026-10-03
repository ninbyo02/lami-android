# Neural TTS NPU feasibility pilot

## Result

The installed LiteRT Torch Qwen3-TTS exporter successfully converted the reference Qwen3-TTS 0.6B Base code predictor (MTP) to a 220,385,520-byte FP16-weight TFLite model. The model filename is upstream mtp_fp32.tflite, but the export recipe uses FP16 weights. Host CPU execution passed 17 sequential synthetic positions: finite outputs, 15x2048 logits, and unchanged cache entries outside each written position. This is not NPU execution, source numerical parity, a speech quality test, or evidence that the production trained checkpoint has been exported.

The real checkpoint matches exporter constants: five layers, hidden size 1024, intermediate size 3072, 16 attention heads, eight KV heads, head dimension 128, vocabulary 2048, and cache capacity 32. The preflight rejects missing real safetensors; upstream otherwise falls back to random weights.

## Why investigate NPU

Device profiling of the CPU baseline found main forward 1,976/3,083 ms and CP forward 1,532/2,665 ms for the two requests, plus CP heads 477/823 ms. CP cache copies were only 27/46 ms. Allocation reuse preserved outputs but did not materially improve latency. Model compute is therefore the stronger target.

ExecuTorch 1.4 has a Qualcomm HTP backend with model lowering and configurable quantization; it does not make existing XNNPACK PTE artifacts runnable on NPU automatically. An alternative is the existing LiteRT Qualcomm toolchain. A successful LiteRT export alone does not establish supported NPU delegation.

Official reference: https://docs.pytorch.org/executorch/1.4/backends-qualcomm.html

## Remaining pilot gates

1. Compile MTP for SM8750 and inspect delegation/unsupported operations, especially dynamic cache updates, attention, normalization and position-dependent RoPE.
2. Preserve cache on device across the 16 prediction steps where possible. Measure transfer and dispatch overhead as well as kernel time.
3. The current upstream exporter computes all 15 output heads at every step. Existing CPU code selects the required head. Avoid this extra work before assessing speed; otherwise a faster accelerator may lose its advantage.
4. Compare model outputs to the matching source checkpoint before generated-code and voice-quality evaluation. FP16/quantization can change sampling and therefore PCM; the previous exact-PCM tests do not apply automatically.
5. Measure TTS alone, then alongside the existing NPU LLM: first audio, frames per second, playback underruns, memory and LLM latency. Shared NPU contention can offset gains.
6. Establish exact production trained-weight provenance before replacement. Leave ordinary chat behavior and the installed device APK unchanged during the pilot.

No speedup multiplier is claimed. Streaming currently starts earlier but underruns; compute acceleration must produce sustained audio rather than only improve the first chunk.

## Reproduction

Run python scripts/voice/probe_litert_mtp.py --model MODEL_DIR --report REPORT.json with safetensors installed. Add --export-dir OUTSIDE_GIT_DIR in the LiteRT Torch environment to export the actual checkpoint. Shape preflight is repeatable and read-only on model files. Large model artifacts are excluded from this PR.

Evidence: 2026-10-03-neural-tts-npu-preflight.json and 2026-10-03-neural-tts-npu-host-smoke.json. Host timing includes Python checks and is not a device benchmark.


## SM8750 device pilot update

Compilation and direct QNN inference now succeeded using host and device SDK 2.47.0.260601. The first attempt lacked host QNN libraries; SDK 2.44 then failed its system API version check (1.10 versus required 1.11). SDK 2.47 resolved the toolchain issue without changing the Android app.

The compiled model contains one DISPATCH_OP, with no remaining CPU operations in its TFLite graph. The extracted 220,880,896-byte context loaded and graph retrieval succeeded on SM8750. QNN estimated DSP context memory at 224.31 MiB; this is an estimate, not total app PSS.

qnn-net-run executed the same position-zero synthetic input 20 times with zero caches, all 15 output heads, native float32/int32 files, default performance profile and basic profiling. Mean NetRun graph execution was 24.521 ms, QNN execution 24.486 ms, and accelerator execution 21.389 ms. Init was 48.351 ms in the NetRun profile; this is not Android first-audio latency. No same-device CPU run of the identical graph was performed, so no speedup is claimed.

All 11 device outputs were finite. Against the FP16-weight LiteRT CPU model, logits max absolute difference was 0.0865655 and RMS difference 0.0155463. All 15 argmax indices agreed for this one input. This does not prove sampled-code equivalence or voice quality, and repeated position zero does not validate carried-cache behavior.

The next optimization target is avoiding computation of all 15 heads at every step, then measuring identical graphs on CPU and NPU and testing actual carried-cache inputs. The installed app was not replaced.

Reproduce compilation with python scripts/voice/compile_litert_mtp.py --model MTP_TFLITE --output-dir OUTSIDE_GIT_DIR --report REPORT.json in the LiteRT environment with matching host Qualcomm SDK libraries in LD_LIBRARY_PATH. The script requires one dispatch graph, extracts its context and rejects failed compilation. For direct device tests use the matching qnn-net-run with --retrieve_context, --use_native_input_files, --use_native_output_files, --num_inferences 20 and --profiling_level basic. Input ordering/types come from qnn-context-binary-utility graphInputs metadata. Device runtime library paths must point to the matching SDK, not an arbitrary installed application runtime.

Evidence: 2026-10-03-neural-tts-npu-compile.json, 2026-10-03-neural-tts-npu-device.json and 2026-10-03-neural-tts-npu-profile.txt. Compilation report describes the reproducibility run; the device report separately binds the tested context by SHA256.
