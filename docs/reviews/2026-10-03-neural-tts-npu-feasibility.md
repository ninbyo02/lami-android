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


## Head-free shared backbone optimization

Added a vendored Apache-2.0 adaptation of the installed LiteRT Torch MtpStep. Only the final 15-head projection is removed; hidden state and all ten cache tensors are returned. Head weights are rejected by the backbone constructor. Existing callers must compute the one required original head on CPU. This avoids duplicating the five-layer backbone into 15 separate models. Ordinary Android chat is unchanged.

The export script checks the real checkpoint and verifies exact PyTorch logits (by reapplying the original heads) and all cache outputs against upstream MtpStep for one synthetic input before conversion. The FP16-weight model shrank from 220,385,520 to 157,433,152 bytes, removing 31,457,280 head weight elements. SM8750 compilation again produced one dispatch operation with no remaining CPU graph operations. Direct QNN execution passed 20 repetitions.

Initial average NetRun timings were 24.521 ms for all heads (earlier turn) and 2.968 ms for backbone only. A paired repeat in the same test session measured **4.181 ms all-heads and 3.051 ms backbone**, each over 20 repetitions with identical inputs/runtime/default performance settings. The dramatic change in the all-head baseline means the initial numbers must not be interpreted as an 8x architectural speedup. Paired backbone-only time was 27.03% lower; selected CPU head time is excluded. This is not end-to-end TTS latency or a proven improvement over the existing Android CPU implementation.

Device backbone hidden/cache outputs were finite. Host CPU projection of its hidden state using FP16-rounded heads matched all 15 reference argmax indices for the test input; logits max absolute difference was 0.0873415 and RMS difference 0.0155378. This remains a single synthetic position-zero check, not speech quality or sampled-code equivalence.

Reproduce export with python scripts/voice/export_litert_mtp_backbone.py --model MODEL_DIR --output-dir OUTSIDE_GIT_DIR. Compile using the existing compile_litert_mtp.py command. Device testing uses the same 13 input files as the preceding full-head model; output logits are replaced by a 1x1024 hidden tensor. Reuse of NPU cache between real generation steps and actual caller head latency remain the next gates.

Evidence: 2026-10-03-neural-tts-npu-backbone.json and the paired repeat profile files. No production model or APK was replaced.


## Actual NPU cache carry verification

The check_npu_mtp_rollout.py probe completed positions 0 through 16, feeding each actual device NPU cache output into the following step. All outputs were finite and all ten caches remained bitwise unchanged outside the current written position. The CPU reference received the same NPU-produced input cache for each comparison; these differences are local step errors, not accumulated divergence between independent CPU/NPU rollouts.

This uses synthetic embeddings and a causal mask. Each step reloads the QNN context and uses ADB/file transfers. It proves the tested cache update/carry behavior, not resident-model throughput, selected CPU head cost, sampling equivalence or voice quality. Neither production assets nor the installed application were replaced. Next gate: a persistent native runner that retains the context and runs the selected CPU head, followed by the matching CPU baseline and real generation inputs.

Evidence: 2026-10-03-neural-tts-npu-rollout.json. Reproduce with scripts/voice/check_npu_mtp_rollout.py --help; it requires the compiled backbone context and matching QNN runtime already staged in a dedicated device directory.

Numerical caveat: maximum hidden-state absolute difference versus the same-input LiteRT CPU reference reached 3.12236 during rollout. No acceptance threshold was applied. Cache structural correctness and finite outputs must not be described as a numerical accuracy pass. Before adoption, investigate the position-dependent difference using representative generation inputs and selected-head logits/code comparisons.


## Numerical investigation: adoption gate blocked

Analyzed the saved 17-step rollout with three paths receiving exactly the same NPU-produced cache input: original FP32 PyTorch backbone, exported FP16-weight LiteRT CPU backbone, and NPU output. Applied the same original FP32 output heads on the host to each hidden state. LiteRT CPU and FP32 source agreed on all 255 head argmax comparisons; NPU versus CPU disagreed on seven (including five at position 8). These are synthetic inputs and all heads, not the real generation schedule, so seven mismatches must not be reported as a speech error rate.

At position 8 the CPU/source hidden relative L2 error was approximately 0.00001026, versus NPU/CPU 0.181724. NPU/CPU logits relative L2 error was 0.175791. Weight conversion alone does not explain this observed discrepancy; the NPU execution/lowering/precision path needs further isolation. No individual operator has yet been identified as the cause.

Replayed identical position-8 embedding, mask and saved position-7 NPU cache three times on device. The report records repeatability against the original saved output. This distinguishes a reproducible numerical discrepancy from an unverified random or transfer failure.

Current decision: do not adopt this FP16 NPU model in ordinary TTS. Next isolate per-layer errors, then validate using actual prepared voice generation inputs and the actual selected-head schedule. Consider keeping sensitive normalization/attention operations in higher precision or on CPU, or calibrated quantization, only after identifying the affected operator. Fast synthetic throughput is insufficient for adoption.

Evidence: 2026-10-03-neural-tts-npu-accuracy.json. Offline analysis is reproducible with scripts/voice/analyze_npu_mtp_rollout.py using the saved rollout output directory, reference checkpoint and CPU TFLite model.

## Layer trace device diagnosis (2026-10-03 16:15 JST)

Added opt-in `--trace-layers` export; the default backbone outputs and normal chat remain unchanged. The trace model exposes each attention residual, MLP residual and SDPA output. Analysis currently uses the named residual outputs; the QNN SDPA output aliases require metadata mapping before use. SM8750 compile retained one DISPATCH_OP and zero CPU ops.

Exact position-8 replay reproduces the original NPU final hidden bitwise, so tracing did not change the observed final arithmetic for this input. CPU versus source final relative L2 is 0.00001026; NPU versus CPU is 0.181724. The first pronounced jump occurs in layer index 2 (third layer): attention residual max absolute error 0.005657 / relative L2 0.005068, then MLP residual max absolute error 0.325020 / relative L2 0.034171. Subsequent layers amplify this difference; this localizes an amplification boundary, not a proven faulty individual operator.

Position-zero control, after explicitly pushing matching host inputs to a dedicated device directory, also matches the original NPU final hidden bitwise. Final NPU/CPU max absolute error is 0.067988 and relative L2 0.007039. The first control attempt used stale device input files and was discarded; the checked-in control report is the fresh-input rerun.

Validation: exact source logit/cache parity during trace export, both device traces, Python compilation and git diff whitespace checks. Saved results: `2026-10-03-neural-tts-npu-trace.json` and `2026-10-03-neural-tts-npu-trace-control.json`. Device execution was isolated under /data/local/tmp; no installed APK was replaced.

Next: isolate layer-2 post-attention normalization and gate/up/SiLU/product/down projection using identical input tensors, then evaluate precision or CPU partition changes. These are synthetic reference-base inputs; no production voice quality or sampled generation equivalence has been established. NPU adoption remains blocked.

## Isolated layer-2 MLP diagnosis (2026-10-03 16:30 JST)

The detailed trace exposes post-attention normalization, gate/up projections, SiLU, product and down projection. Unlike the earlier residual-only trace, this additional instrumentation changes the final NPU hidden (max absolute 0.120117 versus original); its results cannot be treated as a bitwise trace of the original fused graph.

Detailed trace NPU/CPU relative L2: normalized input 0.005122, gate 0.005529, up 0.005328, SiLU 0.006625, product 0.044135, down 0.050070. Replaying each operator on CPU with its actual NPU input instead gives relative errors around 0.00018–0.00030 for gate/up/SiLU/product/down. This does not identify one broken arithmetic operator; it suggests incoming error amplification.

Exported a separate layer-2 MLP (normalization through residual) and compiled to one SM8750 dispatch, context 18,960,384 bytes. Ran two identical-input CPU/NPU comparisons: CPU-attention input max error 0.005058 / relative L2 0.002906, NPU-attention input max error 0.007529 / relative L2 0.002923. Crucially, running both inputs through CPU alone produces max output difference 0.332550 / relative L2 0.034878, from attention input difference max 0.005657 / relative L2 0.005068. The isolated CPU MLP on CPU input matches the full CPU trace exactly.

Inference: moving only this MLP to CPU is unlikely to remove the already-propagated error; precision or partition work must include the preceding layers. This is input sensitivity evidence on one synthetic position, not proof of a specific failing kernel, and not production voice evaluation. Next compare higher-precision preceding-layer computation or a CPU prefix/NPU suffix with identical caches before selecting an Android integration.

Validation: source export parity, detailed trace device execution, isolated two-input device execution, Python compilation, whitespace checks. Results are `2026-10-03-neural-tts-npu-mlp-detail.json` and `2026-10-03-neural-tts-npu-mlp-isolated.json`. App and production speech path are unchanged.

## CPU prefix / NPU suffix comparison (2026-10-03 16:37 JST)

Added opt-in `start_layer` (default zero) to the diagnostic backbone, separate suffix exporter, and metadata-ordered device checker. Export source split parity passed exactly at position zero for starts 3 and 4. Each suffix compiled to one dispatch; prefix cache bypass inputs are absent from the QNN graph, so the checker validates and uses the actual context input metadata. Context bytes: start 3 = 63,143,936; start 4 = 31,617,024.

Across the same 17 saved synthetic positions / 255 all-head argmax comparisons:

| Path | CPU/NPU layers | Argmax mismatches against full CPU | Position-8 hidden relative L2 |
| --- | --- | --- | --- |
| Full NPU | 0/5 | 7 | 0.181724 |
| CPU prefix 3 | 3/2 | 3 | 0.009166 |
| CPU prefix 4 | 4/1 | 2 | 0.003598 |

Both split paths remove all position-8 head mismatches, but leave mismatches elsewhere. Start-3 mismatches: position 4/head 4, position 6/head 5, position 9/head 8. Start-4 mismatches: position 9/head 8, position 14/head 1. These sets differ from the full NPU mismatch set; aggregate reduction does not mean every individual position improves monotonically.

CPU executes on the host and NPU on SM8750; these are numerical experiments, not an Android combined performance benchmark. Every step reuses the saved original NPU cache inputs, so independent split-path rollout/cache drift is still untested. Actual generation sampling, production voice, combined memory, handoff overhead and speed remain unvalidated. Keeping four of five layers on CPU may sacrifice most potential NPU speed benefit; do not claim a speed improvement from this experiment.

Default backbone versus upstream MtpStep exact logit/cache parity passed at saved positions zero and eight after the refactor. Python compilation and whitespace checks passed. Results: `2026-10-03-neural-tts-npu-suffix-3.json` and `2026-10-03-neural-tts-npu-suffix-4.json`. No APK replaced; no production path enabled.

Decision: neither split passes numerical agreement yet. Before Android integration, evaluate actual production inputs and independent cache rollout; only then compare device-resident combined performance. A supported higher-precision/calibrated NPU path remains another investigation, not a completed fix.
