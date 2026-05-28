# QAIRT244 512 Sequence / Prefill Constraint Findings

Date: 2026-05-28

Scope: dev-only investigation assets only. This work does not connect
Backend.NPU to the standard chat route, does not connect DB/TTS/Markdown/
streaming, does not hide fallback, does not change GPU/CPU/held-official
flows, and does not modify production inference behavior.

## Added Investigation Scripts

### Static `.litertlm` scan

Script:

```bash
scripts/check_litertlm_512_sequence_constraints.sh <path-to-sm8750.litertlm>
scripts/check_litertlm_512_sequence_constraints.sh /path/to/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

Purpose:
- scan local `.litertlm` files for strings/metadata related to `512`, `seq`,
  `sequence`, `sequential`, `prefill`, `context`, `max_tokens`,
  `max_seq_len`, and `input_length`;
- save only text evidence;
- avoid staging or committing model binaries.

Initial preflight artifact created without model input:

```text
artifacts/qairt244_litertlm_512_sequence_constraints/20260528_081053/
```

Real-path static scans are now complete for both the SM8750 Qualcomm-targeted
file and the regular E2B file:

| model | artifact | size | sha256 | sequence_candidate_hit_count |
| --- | --- | ---: | --- | ---: |
| SM8750 Qualcomm `.litertlm` | `artifacts/qairt244_litertlm_512_sequence_constraints/20260528_083125/summary.md` | `2.9G` | `41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e` | 275 |
| regular E2B `.litertlm` | `artifacts/qairt244_litertlm_512_sequence_constraints/20260528_083149/summary.md` | `2.5G` | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | 470 |

Input paths:

```text
/home/sato/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm
/home/sato/Downloads/gemma-4-E2B-it.litertlm
```

Interpretation:
- the SM8750 file is not uniquely rich in 512/sequence string candidates;
  the regular E2B file has more sequence candidate hits in this scan;
- the SM8750 `metadata_candidates` output did not show explicit
  `prefill=512`, `sequence_length=512`, `max_seq_len=512`,
  `context_length=512`, or `input_length=512` metadata;
- the SM8750 strings include entries such as
  `LanguageModel.decode_graph/.../ElementWiseBinary_4079_LiteRt_OpId_512`
  and `LanguageModel.decode_graph/.../FullyConnected_4512_LiteRt_OpId_895`,
  but `OpId_512` is most likely a LiteRT operator id and is not treated as
  evidence for a sequence-length or prefill constraint;
- static scan does not support the 512 sequential/prefill hypothesis, but it
  also cannot disprove a compiled graph shape limit because that limit may not
  be represented as readable strings or simple metadata.

### Dev-only sequence/prefill probe

Script:

```bash
scripts/run_npu_512_sequence_probe.sh
scripts/run_npu_512_sequence_probe.sh --execute --timeout 60 --max-output-tokens 16
```

Default mode is preflight-only and does not execute NPU. `--execute` is
required for runtime probing. The runtime mode assumes a standardDebug build
with the QAIRT244 max512 native guard and hidden receiver route is already
installed; the script deliberately does not stage native libraries or rebuild
QAIRT.

Probe matrix:
- templates: `raw`, `simple_ja_chat`, `gemma_it_like`
- approximate final-input token targets: `1`, `8`, `16`, `32`, `64`, `128`,
  `256`, `384`, `512`, `640`
- max output tokens default: `16`, to minimize decode-length confounding and
  focus on prefill/input-length behavior
- prompt filler uses compact `x ` tokens so the existing 128-codepoint hidden
  app validation gate is not tripped earlier than necessary

Preflight artifact:

```text
artifacts/qairt244_npu_512_sequence_probe/20260528_081151/
```

The preflight artifact records the full 30-case matrix and the reproduction
command. No runtime cases were executed in this pass. With compact `x ` filler
and the current 128-codepoint hidden-route gate, preflight expects native entry
to be possible up to roughly `raw=64`, `simple_ja_chat=32`, and
`gemma_it_like=32` target tokens; larger targets are expected app-side prompt
validation rejects before native.

Therefore the current 512/640 rows are not graph constraint checks. They are
expected to hit the 128-codepoint hidden-route prompt gate first, before
native entry, so they cannot prove or disprove a 512 sequence/prefill limit in
the `.litertlm` graph.

An execution attempt was also recorded at:

```text
artifacts/qairt244_npu_512_sequence_probe/20260528_081608/
```

It stopped before any NPU case because no device was connected
(`adb devices` returned an empty device list). No runtime matrix rows were
executed.

A later connected-device runtime attempt was manually interrupted after the
app displayed Android's "Lami is not responding" dialog during probe startup:

```text
artifacts/qairt244_npu_anr_probe/20260528_205140/
```

That artifact was not enough to classify NPU/runtime behavior. `logcat.txt`
had `0` lines, `lami_ps.txt` had `0` lines, and `dumpsys activity anr` was not
supported on the device (`Unknown command: anr`). This means the failure
happened before useful 512 sequential evidence was collected. Before any
further runtime probing, the runner must collect safer diagnostics and must
avoid getting stuck if `am broadcast` does not return.

The probe runner was therefore hardened for future runs with:
- `--limit-cases`, so the next connected-device check can execute only the
  first matrix row;
- an outer timeout around `adb shell am broadcast`;
- `am force-stop io.github.ninbyo02.lami` after broadcast or receiver-state
  timeout;
- interrupt handling that force-stops the app and saves interrupt diagnostics
  if the runner is stopped manually;
- saved diagnostics for `logcat -d -v time`, logcat clear result, dumpsys
  window/activity/input variants, `ps -A`, `pidof`, readable
  `/data/anr/traces.txt`, and dropbox ANR/crash/tombstone tags.

### First safe runtime probe result: raw target=1

The hardened one-case runtime probe completed without timeout or fallback:

```text
artifacts/qairt244_npu_512_sequence_probe/20260528_212207/
```

Observed case:
- template: `raw`
- target: `1`
- status: `failure`
- timeout: `false`
- native reached: `true`
- decode reached: `true`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback: `false`
- fresh crash: `false`

This is not 512 sequence/prefill boundary evidence. The first useful finding
is instead a max-output-token propagation and output-quality classification
problem:
- broadcast requested `max_output_tokens=16`;
- the hidden receiver and route metadata record
  `requested_max_output_tokens=16` and effective `max_output_tokens=16`;
- native diag records entry with `max_output_tokens=16`, but immediately
  before decode it records `SetMaxOutputTokens(512)`,
  `native_max_output_tokens_limit=512`, and
  `qairt244_editable_prompt_max512_v1`;
- `result.txt` therefore has native-written leading metadata with
  `max_output_tokens=512`, followed later by route-appended metadata with
  `max_output_tokens=16`;
- raw native output was `768` characters of repeated `"\n\nx"` and
  `output_unicode_summary` reported `control_chars=U+000Ax512`;
- the sanitizer treated repeated prompt echo lines as prompt echo
  (`removed_prompt_echo=true`) and produced `sanitized_output_length=0`;
- final failure was `reasonCode=empty_after_sanitize` at
  `failure_stage=native_result`, even though decode itself completed.

Current interpretation: the runtime has reached the NPU decode path, but the
installed native artifact appears to keep the max512 native decode cap active
at `SetMaxOutputTokens(512)` despite the hidden route requesting/effectively
recording `16`. The raw output also repeats the prompt token, so this line of
investigation should shift away from 512 sequence length and toward
`max_output_tokens` propagation, native result metadata layering, and output
quality/sanitizer classification.

### Installed native artifact static scan

Before rerunning runtime probe, the installed APK and current local native
build artifacts were scanned for the max512 marker:

```text
artifacts/qairt244_native_max_output_static_scan/20260528_static_preflight/summary.md
```

Device APK path:

```text
package:/data/app/~~B4eK-DcXwwGOMXJvFLuIsw==/io.github.ninbyo02.lami-P1h-FJ3hcWAUx7djtn9eRA==/base.apk
```

Pulled APK:

```text
artifacts/qairt244_native_max_output_static_scan/20260528_static_preflight/installed_base.apk
```

The installed APK contains `lib/arm64-v8a/liblitertlm_jni.so`, extracted as:

```text
artifacts/qairt244_native_max_output_static_scan/20260528_static_preflight/apk_libs/liblitertlm_jni.so
```

Static strings in the installed arm64 library include:

```text
qairt244_editable_prompt_max512_v1
%s before RunDecode SetMaxOutputTokens(512) native_max_output_tokens_limit=512 max_output_tokens_limit_marker=%s
%s invalid_max_output_tokens value=%d native_max_output_tokens_limit=512 max_output_tokens_limit_marker=%s
invalid_max_output_tokens_limit_512
native_max_output_tokens_limit=%d
max_output_tokens_limit_marker=%s
max_output_tokens=%d
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
Invalid FastRPC buffer address
Invalid FastRPC buffer fd
```

The installed library sha256 is:

```text
7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105
```

That sha256 exactly matches the current `standardDebug` local build artifacts:

```text
app/build/generated/qairt244StandardDebugJniLibs/arm64-v8a/liblitertlm_jni.so
app/build/intermediates/merged_native_libs/standardDebug/mergeStandardDebugNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so
app/build/intermediates/stripped_native_libs/standardDebug/stripStandardDebugDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so
```

Conclusion: the installed APK and current local build artifact are consistent,
and both are max512 marker builds. This strengthens the hypothesis that the
Kotlin route can request/record `16` while the installed native decode cap is
still the max512 path. Before rerunning a `--max-output-tokens 16` propagation
check, the safe next step is to stage a dev-only native artifact that either
uses the requested decode cap or records the exact requested value at
`SetMaxOutputTokens(...)`.

### Native requested max output propagation target

Static source inspection found the max512 fixed decode cap in the local
LiteRT-LM checkout, not in the Kotlin route:

```text
/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
```

Relevant source locations in that checkout:

```text
108: kQairt244EditablePromptMax512Marker = "qairt244_editable_prompt_max512_v1"
119: kQairt244EditablePromptMaxOutputTokensLimit = 512
1795: Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt(...)
1921..1934: validates max_output_tokens is in 1..512, otherwise returns invalid_max_output_tokens_limit_512
2131..2134: logs SetMaxOutputTokens(512), then calls decode_config.SetMaxOutputTokens(512)
```

The important split is that the JNI method already receives
`jint max_output_tokens` from the route and validates the range against
`1..512`, but the decode call ignores the accepted value and hard-codes
`512`. The result writer also records `max_output_tokens` as the fixed native
limit rather than the requested/effective value.

Minimal native patch direction for a later, separately approved change:

```diff
- constexpr char kQairt244EditablePromptMax512Marker[] =
-     "qairt244_editable_prompt_max512_v1";
+ constexpr char kQairt244EditablePromptRequestedMaxMarker[] =
+     "qairt244_editable_prompt_requested_max_v1";

- std::fprintf(file, "max_output_tokens=%d\n",
-              kQairt244EditablePromptMaxOutputTokensLimit);
+ std::fprintf(file, "requested_max_output_tokens=%d\n", requested);
+ std::fprintf(file, "effective_max_output_tokens=%d\n", effective);
+ std::fprintf(file, "max_output_tokens=%d\n", effective);

- "%s before RunDecode SetMaxOutputTokens(512) native_max_output_tokens_limit=512 ..."
- decode_config.SetMaxOutputTokens(512);
+ "%s before RunDecode SetMaxOutputTokens(%d) requested_max_output_tokens=%d effective_max_output_tokens=%d native_max_output_tokens_limit=512 ..."
+ decode_config.SetMaxOutputTokens(effective_max_output_tokens);
```

Patch constraints:
- keep the accepted native range explicit: `1..512`;
- reject invalid values as failure and do not silently fall back to `512`;
- set `effective_max_output_tokens = max_output_tokens` after validation;
- keep `native_max_output_tokens_limit=512` as the upper bound;
- update the marker to `qairt244_editable_prompt_requested_max_v1`;
- include both `requested_max_output_tokens` and
  `effective_max_output_tokens` in result and diag output;
- log the actual `SetMaxOutputTokens(<effective>)` value.

The built `liblitertlm_jni.so` is produced by:

```text
scripts/build_litert_custom_artifacts.sh
target: //kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni
artifact output: artifacts/litert_custom_build/<timestamp>_<label>/built_libs/liblitertlm_jni.so
```

The currently installed max512 artifact was recorded at:

```text
artifacts/qairt244_editable_prompt_max512_entrypoint_build/20260526_235239/built_libs/liblitertlm_jni.so
sha256=7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105
```

StandardDebug packaging path:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so
app/build/generated/qairt244StandardDebugJniLibs/arm64-v8a/liblitertlm_jni.so
app/build/intermediates/merged_native_libs/standardDebug/mergeStandardDebugNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so
app/build/intermediates/stripped_native_libs/standardDebug/stripStandardDebugDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so
```

The Gradle wiring is in `app/build.gradle.kts`:
- `stageQairt244StandardDebugNativeLibs` copies `.so` files from
  `src/customBuildExperimentDebug/jniLibs/arm64-v8a` to
  `build/generated/qairt244StandardDebugJniLibs/arm64-v8a`;
- `overlayQairt244StandardDebugNativeLibs` overlays generated libs into
  `mergeStandardDebugNativeLibs`;
- `overlayQairt244StandardDebugStrippedNativeLibs` keeps the staged libs after
  AGP strip;
- `packageStandardDebug` depends on the stripped overlay task.

Candidate rebuild/stage commands for a later approved implementation phase:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_requested_max

scripts/stage_litert_custom_build_stack_for_experiment.sh \
  artifacts/litert_custom_build/<timestamp>_qairt244_requested_max

./gradlew :app:assembleStandardDebug
```

Static verification before any runtime probe:

```bash
strings artifacts/litert_custom_build/<timestamp>_qairt244_requested_max/built_libs/liblitertlm_jni.so |
  grep -E 'qairt244_editable_prompt_requested_max_v1|SetMaxOutputTokens|requested_max_output_tokens|effective_max_output_tokens|native_max_output_tokens_limit'

strings app/build/generated/qairt244StandardDebugJniLibs/arm64-v8a/liblitertlm_jni.so |
  grep -E 'qairt244_editable_prompt_requested_max_v1|SetMaxOutputTokens|requested_max_output_tokens|effective_max_output_tokens|native_max_output_tokens_limit'

unzip -p app/build/outputs/apk/standard/debug/app-standard-debug.apk \
  lib/arm64-v8a/liblitertlm_jni.so > /tmp/lami_standard_debug_liblitertlm_jni.so

strings /tmp/lami_standard_debug_liblitertlm_jni.so |
  grep -E 'qairt244_editable_prompt_requested_max_v1|SetMaxOutputTokens|requested_max_output_tokens|effective_max_output_tokens|native_max_output_tokens_limit'
```

At the time of this source-inspection pass, this remained only a dev-only
native artifact investigation target. No source change, APK rebuild, install,
or runtime probe was performed in that pass.

### Requested max output runtime confirmation

The requested-max native artifact was built, staged into the standardDebug
APK, installed manually, and checked with one hardened runtime probe. The APK
contained the requested-max `liblitertlm_jni.so`:

```text
sha256=3ad4b291d1014ff61e57a9de634f317b8343d9293955230e7128b3633c5d7b7a
marker=qairt244_editable_prompt_requested_max_v1
```

One case was then run:

```bash
scripts/run_npu_512_sequence_probe.sh \
  --execute \
  --device 192.168.52.52:41591 \
  --timeout 60 \
  --max-output-tokens 16 \
  --limit-cases 1
```

Artifact:

```text
artifacts/qairt244_npu_512_sequence_probe/20260529_045504
```

Observed case:
- template: `raw`
- target: `1`
- status: `failure`
- timeout: `false`
- native reached: `true`
- decode reached: `true`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback: `false`
- fresh crash: `false`

Max-output propagation result:
- requested max output tokens: `16`
- effective max output tokens: `16`
- native limit: `512`
- native first max output tokens: `16`
- marker: `qairt244_editable_prompt_requested_max_v1`
- native diag recorded `SetMaxOutputTokens(16)`

This confirms that the requested max output value now reaches the native
decode cap. The prior max512 build produced `native first max=512` and
`raw_native_output_length=768`; this requested-max build produced
`native first max=16` and `raw_native_output_length=24`.

The remaining failure is not a max-output propagation failure:
- `reason=empty_after_sanitize`
- `raw_native_output_length=24`
- `sanitized_output_length=0`
- `quality=mixed_language`
- `control_chars=true`
- `decode_elapsed_ms=362`

Current interpretation: NPU decode still reaches the native path without
fallback or fresh crash, and the fixed `SetMaxOutputTokens(512)` problem is
resolved. The next blocker is output classification. Because this row uses the
raw prompt `x`, the native output's `"\n\nx..."` pattern may be prompt echo
that the sanitizer removes completely. The next investigation should therefore
separate sanitizer/echo handling from NPU decode behavior, for example with a
single explicit non-`x` prompt case. That requires script support such as
`--prompt`, `--only-template raw`, and `--only-target 1`, but no sanitizer
change or rerun was performed in this pass.

The probe runner was then extended, without runtime re-execution, with
script-only controls for this sanitizer/echo split:

```bash
scripts/run_npu_512_sequence_probe.sh \
  --dry-run \
  --prompt "こんにちは" \
  --only-template raw \
  --only-target 1 \
  --limit-cases 1
```

The dry-run summary now records the selected case and actual custom prompt
length before execution. For the example above it selects only `raw target=1`,
uses prompt source `custom`, records `final_input_chars_approx=5`, and marks
`native_pre_reject_expected_by_128_gate=false`.

The same single selected case was then executed with the custom prompt:

```bash
scripts/run_npu_512_sequence_probe.sh \
  --execute \
  --device 192.168.52.52:41591 \
  --timeout 60 \
  --max-output-tokens 16 \
  --prompt "こんにちは" \
  --only-template raw \
  --only-target 1 \
  --limit-cases 1
```

Artifact:

```text
artifacts/qairt244_npu_512_sequence_probe/20260529_050810/summary.md
```

Result:
- status: `success`
- timeout: `false`
- native reached: `true`
- decode reached: `true`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback: `false`
- fresh crash: `false`
- requested max output tokens: `16`
- effective max output tokens: `16`
- native limit: `512`
- native first max output tokens: `16`
- raw output length: `34`
- sanitized output length: `34`
- quality: `natural_japanese`
- control chars observed: `true`

This confirms that requested max-output propagation remains correct and that
NPU decode still succeeds with the requested-max native artifact. The earlier
`raw target=1` failure with generated `x` filler was therefore likely a
prompt-design/sanitizer-echo artifact, not a decode failure. Future boundary
probes should avoid raw `x` filler when the goal is output quality or
sanitizer classification; use natural language or another prompt that is less
likely to be removed as echo.

Additional one-case natural-language custom prompt runs then expanded the raw
path evidence without changing Kotlin/native code:

| artifact | template | target | prompt | final chars | status | native/decode | requested/effective | raw/sanitized len | quality | control chars |
| --- | --- | ---: | --- | ---: | --- | --- | --- | --- | --- | --- |
| `artifacts/qairt244_npu_512_sequence_probe/20260529_051721/summary.md` | `raw` | 8 | `こんにちは。短く返答してください。` | 17 | `success` | `true/true` | `16/16` | `12/11` | `natural_japanese` | `false` |
| `artifacts/qairt244_npu_512_sequence_probe/20260529_052320/summary.md` | `raw` | 16 | `日本語で一言だけ挨拶してください。` | 17 | `success` | `true/true` | `16/16` | `8/6` | `natural_japanese` | `true` |
| `artifacts/qairt244_npu_512_sequence_probe/20260529_052442/summary.md` | `raw` | 32 | `日本語で一言だけ挨拶してください。` | 17 | `success` | `true/true` | `16/16` | `8/6` | `natural_japanese` | `true` |
| `artifacts/qairt244_npu_512_sequence_probe/20260529_052914/summary.md` | `raw` | 64 | `日本語で一言だけ挨拶してください。` | 17 | `success` | `true/true` | `16/16` | `8/6` | `natural_japanese` | `true` |

All four raw runs kept `fallback=false`, `fresh_crash=false`, and
`npu_evidence=QNN_HTP_V79_FastRPC_native_diag`. Together with the earlier
`raw target=1` custom prompt success, the raw custom prompt path has now
reached and decoded successfully for targets `1`, `8`, `16`, `32`, and `64` in
one-case guarded runs.

A matching `simple_ja_chat target=1` run with the same natural prompt reached
native/decode but still failed after sanitization:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_052004/summary.md
prompt=こんにちは。短く返答してください。
final_input_chars_approx=43
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
raw_len=35
sanitized_len=0
quality=natural_japanese
```

The raw output included a Thai greeting and echoed template-like lines:

```text
สวัสดี。
ユーザー: 短्टく返答してください。
アシスタント
```

Interpretation: `simple_ja_chat` reaches the same native/decode path, but its
template echo and mixed-script output can still be fully removed by the
sanitizer. This is separate from the raw NPU path stability question.

A later custom-prompt run used `--only-target 128` with the same short natural
prompt:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_054242/summary.md
template=raw
target=128
custom_prompt=true
prompt=日本語で一言だけ挨拶してください。
prompt_chars=17
final_input_chars_approx=17
native_pre_reject_expected_by_128_gate=false
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_file_first_max=16
quality=natural_japanese
```

This result confirms that short custom prompts continue to decode even when
the case label is `target=128`, but it is not a 128 gate boundary check and is
not 512 sequential evidence. When `--prompt` is set, the script does not use
the target to generate filler; `target` remains a case label, and
`prompt_chars` / `final_input_chars_approx` are the source of truth. A real
Phase 1 baseline reject requires either the generated-filler raw target `128`
case with no `--prompt`, or an explicit custom prompt whose final input exceeds
the 128-codepoint gate.

The generated-filler raw target `128` Phase 1 check was then run:

```bash
scripts/run_npu_512_sequence_probe.sh \
  --execute \
  --device 192.168.52.52:41591 \
  --timeout 60 \
  --max-output-tokens 16 \
  --only-template raw \
  --only-target 128 \
  --limit-cases 1
```

Artifact:

```text
artifacts/qairt244_npu_512_sequence_probe/20260529_055209/summary.md
```

Observed result:
- custom prompt: `false`
- target length semantics: `generated_x_filler_input_length_approximation`
- input length source of truth: `target_generated_filler_estimate`
- template: `raw`
- target: `128`
- prompt chars: `256`
- final input chars approx: `256`
- preflight expected native-before reject: `true`
- expected validation: `expected_app_prompt_validation_reject`
- status: `failure`
- native reached: `true`
- decode reached: `true`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback: `false`
- fresh crash: `false`
- requested/effective max output tokens: `128/128`
- native limit: `512`
- native first max output tokens: `128`
- raw output length: `192`
- sanitized output length: `0`
- quality: `mixed_language`
- control chars observed: `true`
- reason: `empty_after_sanitize`

This is a preflight prediction mismatch. The runner predicted
`native_pre_reject_expected_by_128_gate=true`, but receiver/native artifacts
show that the hidden receiver path reached native decode. Therefore the
current preflight table is only a heuristic expectation, and the source of
truth is the receiver/result/native artifacts (`native=true`, `decode=true`,
and NPU evidence). The raw target `128` failure is not an app-side validation
reject and is not evidence of a 512 graph/prefill boundary failure; it is a
post-decode sanitizer/output classification failure.

The generated-filler raw target `256` check was then run:

```bash
scripts/run_npu_512_sequence_probe.sh \
  --execute \
  --device 192.168.52.52:41591 \
  --timeout 60 \
  --max-output-tokens 16 \
  --only-template raw \
  --only-target 256 \
  --limit-cases 1
```

Artifact:

```text
artifacts/qairt244_npu_512_sequence_probe/20260529_055701/summary.md
```

Observed result:
- custom prompt: `false`
- target length semantics: `generated_x_filler_input_length_approximation`
- template: `raw`
- target: `256`
- prompt chars: `512`
- final input chars approx: `512`
- preflight expected native-before reject: `true`
- expected validation: `expected_app_prompt_validation_reject`
- status: `failure`
- native reached: `true`
- decode reached: `true`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback: `false`
- fresh crash: `false`
- reason: `empty_after_sanitize`
- requested/effective max output tokens: `128/128`
- native limit: `512`
- native first max output tokens: `128`
- raw output length: `192`
- sanitized output length: `0`
- quality: `mixed_language`
- control chars observed: `true`

This extends the preflight prediction mismatch: raw target `256`
(`final_input_chars_approx=512`) was also predicted to reject before native,
but it reached native/decode. It is still not a 512 graph/prefill failure
because decode completed and the failure was `empty_after_sanitize`.

However, this run exposes a separate unresolved max-output propagation issue.
The command requested `--max-output-tokens 16`, but the recorded
requested/effective/native-first max output values were `128`. That suggests
the target value or another hidden-route clamp/default may be influencing
`max_output_tokens` for generated-filler sequence probes. This should be
investigated as argument propagation before using these generated-filler rows
as clean sequence-boundary evidence.

## Runtime Classification Plan

For each case, the runner records:
- template mode
- approximate prompt/final-input token target
- final input character estimate
- native reached
- decode reached
- editable prompt rejected
- empty output
- fallback_used
- fresh_crash
- `QNN_HTP_V79_FastRPC_native_diag`
- replacement character count
- side-effect flags

The script uses the hidden `StandardHiddenQairt244PromptReceiver` only. It
does not connect standard ChatScreen, assistant list insertion, DB, TTS,
Markdown renderer, streaming, or selectedPath persistence. Each runtime case
is force-stopped before dispatch to isolate input-length/prefill behavior from
sequential resource inheritance.

Important limitation: the current hidden route still has the existing
`NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH=128` codepoint gate.
That means final-input targets above the compact prompt cases that fit within
128 codepoints will be expected to classify as app-side prompt validation
rejects unless a separate dev-only validation bypass is approved. This is
useful for distinguishing "editable prompt rejected" from native sequence
limits, but it does not by itself prove the `.litertlm` graph behavior above
128 codepoints.

Current native-reach expectation under the gate:

| template | native entry expected before 128 gate | native-before reject expected |
| --- | --- | --- |
| `raw` | target `1..64` | target `128,256,384,512,640` |
| `simple_ja_chat` | target `1..32` | target `64,128,256,384,512,640` |
| `gemma_it_like` | target `1..32` | target `64,128,256,384,512,640` |

The full 512 sequential/prefill validation likely needs a separate dev-only
validation bypass so the intended 512/640 final-input cases can reach native.
That bypass is not implemented here and must remain hidden-only: no standard
ChatScreen route connection, no DB/TTS/Markdown/streaming connection, no
selectedPath=NPU persistence, and no fallback hiding.

`HIDDEN_TEMPLATE_MAX_LENGTH=128` grep evidence recorded in this pass:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt:5: const val HIDDEN_TEMPLATE_MAX_LENGTH = 128
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt:110: maxCodePoints = HIDDEN_TEMPLATE_MAX_LENGTH
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt:120: HIDDEN_TEMPLATE_MAX_LENGTH
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt:127: HIDDEN_TEMPLATE_MAX_LENGTH
app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuChatScreenBlockedBranch.kt:180: prompt_input_code_point_limit=${NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH}
```

## Decision Rules

| Observation | Primary hypothesis |
| --- | --- |
| Decode reaches only at or below roughly 512 final-input tokens | NPU compiled graph sequence/prefill fixed-length constraint |
| Failure follows template, independent of target token count | Prompt serialization/template incompatibility |
| `raw` passes but short templated prompts fail | Editable prompt rejection or special-token handling incompatibility |
| 512+ final-input targets still reach native/decode | 512 sequence-limit hypothesis weakens; return to QNN runtime, native non-return, or sanitizer/display path |
| `QNN_HTP_V79_FastRPC_native_diag` disappears only past a boundary | Backend/runtime handoff or graph-shape boundary issue |

## Current Position

Current evidence now separates the max-output issue from prompt design and
sanitizer behavior. The real-path `.litertlm` static scan comparison is
complete and did not find SM8750-specific readable metadata proving `512`
sequence/prefill/context/input length. The requested-max native artifact
reaches NPU decode with `SetMaxOutputTokens(16)`, fallback disabled, and no
fresh crash for raw custom-prompt targets `1`, `8`, `16`, `32`, and `64`.

Current conclusion: the 512 sequential hypothesis is not supported by static
scan evidence, but it remains unclosed. The fixed native max512 decode cap is
no longer the active blocker for the one-case probe, and generated raw `x`
filler is not a reliable sanitizer/echo probe. `simple_ja_chat` is a separate
template echo / mixed-script sanitizer problem: it can reach native/decode and
still collapse to `empty_after_sanitize`. The existing hidden route still
cannot be assumed to reject every target above the 128-codepoint preflight
threshold: generated-filler raw target `128` was predicted to reject before
native, but actually reached native/decode and failed after sanitization; raw
target `256` did the same with `final_input_chars_approx=512`. This weakens
the 512 sequential hypothesis, but does not close it because the raw target
`256` run also showed a max-output recording mismatch (`16` requested on the
command line, `128` recorded in artifacts). For custom-prompt invocations, the
target value remains a case label rather than an input length guarantee.

Policy remains unchanged:
- H1 remains pinned to `max_output_tokens=128`.
- 256 remains the hidden experimental baseline candidate.
- 512 remains `hidden_per_run_isolated_512` candidate only.
- 512 sequential remains incomplete and non-baseline.
- 1024/2048/4096 remain blocked.

## Next Safe Step

1. Treat the SM8750-vs-regular-E2B static scan comparison as complete:

```text
artifacts/qairt244_litertlm_512_sequence_constraints/20260528_083125/summary.md
artifacts/qairt244_litertlm_512_sequence_constraints/20260528_083149/summary.md
```

2. If runtime probing is approved, first run only one hidden matrix row with
   the hardened runner. This is a runner-safety check, not a 512 sequential
   conclusion:

```bash
scripts/run_npu_512_sequence_probe.sh --execute --device 192.168.52.52:41591 --timeout 60 --max-output-tokens 16 --limit-cases 1
```

3. Treat the 128 gate preflight table as a prediction, not ground truth. Before
   probing larger generated-filler targets, inspect script/receiver argument
   propagation for why `--max-output-tokens 16` was recorded as `128` in the
   raw target `256` artifact.

4. If direct 512 graph/prefill boundary evidence is still required, design a
   separately approved dev-only validation bypass that is non-ChatScreen,
   non-persistent, does not connect DB/TTS/Markdown/streaming, and does not
   hide fallback. The design is documented in
   `docs/litert_qairt244_128_gate_bypass_design.md`; no bypass implementation
   exists in this pass. Because raw targets `128` and `256` reached native
   without a bypass, the bypass necessity should be re-evaluated after
   measured gate-condition probes and max-output argument propagation are
   understood.

If later targets reject before native entry, the next safe design step remains
a dev-only, non-ChatScreen, non-persistent validation bypass dedicated to
prefill-length probing. That would be a separate approval because it changes
app-side guard behavior, even if it remains hidden-only.
