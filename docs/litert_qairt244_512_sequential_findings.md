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

However, this run exposed a separate max-output propagation issue. The command
requested `--max-output-tokens 16`, but the recorded
requested/effective/native-first max output values were `128`.

Follow-up code/artifact inspection without rerunning runtime points to adb
broadcast argument transport, not native decode or summary parsing:
- `request.txt` planned `prompt_chars=512` and `final_input_chars_approx=512`,
  but it did not record `max_output_tokens`.
- `broadcast.txt` showed `pkg=x`, which indicates the space-separated generated
  filler prompt (`x x x ...`) was split by the device-side shell/am broadcast
  command line.
- `receiver_state.txt` recorded `prompt=x`, `final_model_input=x`,
  `prompt_input_code_points=1`, `max_output_tokens_compare_enabled=false`,
  and requested/effective `128/128`.
- `native_diag.txt` recorded prompt length `1` and
  `SetMaxOutputTokens(128)`.
- `result.txt` and `case_summary.txt` also consistently recorded
  requested/effective/native-first `128`.

The hidden receiver defaults to `DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS`
when `allow_max_output_tokens_compare` is absent or false, so the most likely
cause is that the space-containing generated filler prompt broke broadcast
extra parsing before the `max_output_tokens=16` and compare flag reached the
receiver. Generated-filler rows that use space-separated prompts should
therefore not be treated as clean sequence-boundary evidence until prompt
transport is made shell-safe, for example by a dev-only base64 or file-backed
prompt extra.

The probe runner and dev-only hidden receiver were updated to use
`prompt_base64` for shell-safe prompt transport. The receiver now decodes
UTF-8 base64 when present and records `prompt_transport`,
`prompt_base64_present`, `prompt_decode_success`, and final input codepoint
metadata in the receiver artifact. The runner records `prompt_transport=base64`
and `prompt_base64_length` in request/summary artifacts and no longer sends
space-containing generated filler prompts directly via `--es prompt`.
Before using generated-filler rows for larger input/prefill checks, rerun a
single guarded dry-run/runtime case and confirm the receiver sees the intended
prompt length and requested max output tokens.

After applying `prompt_base64`, the generated-filler raw target `256` case was
rerun:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_062513/summary.md
template=raw
target=256
custom_prompt=false
prompt_transport=base64
prompt_chars=512
prompt_base64_length=684
final_input_chars_approx=512
native_pre_reject_expected_by_128_gate=true
status=failure
reason=gate_blocked:VALIDATOR_INVALID
native=false
decode=false
npu_evidence=
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=128
native_file_first_max=missing
raw_len=0
sanitized_len=0
quality=empty_output
```

This confirms the transport fix changed the observation: the space-containing
generated filler prompt reached the receiver with its intended length, and raw
target `256` (`final_input_chars_approx=512`) was rejected by the validator
before native entry. The earlier raw target `256` native/decode result is best
treated as a transport false positive caused by the prompt collapsing to `x`
and losing the compare/max-output extras. With shell-safe prompt transport, the
current measured boundary is raw target `64` (`final_input_chars_approx=128`)
inside the gate and raw target `128+` above the gate. Direct 512 sequential or
4096-input/prefill validation still requires a separately approved dev-only,
hidden-receiver-only 128-gate bypass.

Phase 2 preparation added that bypass as an explicit hidden receiver flag:
`--unsafe-dev-bypass-prompt-length-gate`, sent as
`unsafe_dev_bypass_prompt_length_gate=true`. The default remains false. The
flag is only read by `StandardHiddenQairt244PromptReceiver`, is not connected
to standard ChatScreen routing, does not persist backend/UI settings, and only
allows the hidden sequence probe to bypass the `too_long` result from the
128-codepoint hidden-template validator. Other validator failures and all
existing route/model/max-output safety gates remain active. Artifacts now
record `unsafe_dev_bypass_prompt_length_gate_requested`,
`unsafe_dev_bypass_prompt_length_gate_effective`,
`prompt_length_gate_limit`, `prompt_length_gate_would_block`, and
`prompt_length_gate_bypassed`.

The first bypassed raw target `128` runtime attempt then showed that the
receiver/route gate was bypassed, but the debug editable-prompt wrapper still
re-ran the hidden-template prompt-length validator immediately before native
execution:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_072125/summary.md
template=raw
target=128
prompt_transport=base64
prompt_chars=256
final_input_chars_approx=256
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
prompt_length_gate_would_block=true
prompt_length_gate_bypassed=true
requested/effective=16/16
status=failure
reason=adapter_failure:IllegalStateException
native=false
decode=false
message=editable prompt rejected before native execution: reasonCode=too_long
```

The bypass was therefore propagated one step deeper into the debug
`Qairt244ShortMultitokenSmoke.runEditablePrompt` wrapper. That wrapper now uses
the same default-false unsafe flag and only skips the hidden-template
`too_long` prompt-length result. It still preserves model/max-output checks,
empty prompt rejection, invalid UTF-8/control-character validation, and all
other non-length validator failures. Adapter artifacts additionally record
`adapter_prompt_length_gate_would_block`,
`adapter_prompt_length_gate_bypassed`, and `final_model_input_code_points`.

A later bypassed raw target `128` run reached the native C++ entrypoint but was
still rejected by native prompt validation:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_074542/summary.md
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
requested/effective=16/16
status=failure
reason=native_result:invalid_prompt
native_diag prompt_validation reason=too_long
prompt_input_code_points=255
native_prompt_input_code_point_limit=128
native_prompt_input_limit_mode=hidden_template_experiment
```

This shows the receiver, route, and Kotlin debug wrapper gates had been
bypassed, but the native validation layer still enforced the same
hidden-template length gate before usable graph/prefill evidence could be
collected. The Android wrapper now passes
`unsafe_dev_bypass_hidden_template_experiment` as the native
`promptInputLimitMode` only when the explicit unsafe flag is set. The native
source recognizes that mode, preserves the 128 limit as metadata, bypasses only
the `too_long` length result, and records
`native_prompt_length_gate_would_block`,
`native_prompt_length_gate_bypassed`, and
`unsafe_dev_bypass_prompt_length_gate_effective`. This requires a new dev-only
native artifact build before APK/install/runtime verification.

After rebuilding and installing the native length-gate bypass artifact, raw
target `128` succeeded through NPU decode:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_200054/summary.md
command=scripts/run_npu_512_sequence_probe.sh --execute --device 192.168.52.52:34437 --timeout 60 --max-output-tokens 16 --unsafe-dev-bypass-prompt-length-gate --only-template raw --only-target 128 --limit-cases 1
prompt_transport=base64
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
template=raw
target=128
prompt_chars=256
final_input_chars_approx=256
native_pre_reject_expected_by_128_gate=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
control_chars=false
```

This confirms the receiver, route, Kotlin debug wrapper, and native C++ length
gate bypasses are connected for the hidden receiver path. A raw
`final_input_chars_approx=256` input can now cross the original 128-codepoint
gate and complete NPU decode without fallback or fresh crash. This is a
milestone before direct 512 sequential validation; the next decisive case is
raw target `256` (`final_input_chars_approx=512`) with the same one-case,
max-output-16, timeout/force-stop/diagnostics constraints.

The next one-case probe, raw target `256`, also succeeded through NPU decode:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_200533/summary.md
prompt_transport=base64
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
template=raw
target=256
prompt_chars=512
final_input_chars_approx=512
native_pre_reject_expected_by_128_gate=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
control_chars=false
```

This is the first direct bypassed runtime check at
`final_input_chars_approx=512`. Under this measured condition, the 512
sequential hypothesis is not supported: the input reached native, reached
decode, used QNN/HTP/FastRPC evidence, and did not fall back or freshly crash.
This clears the path toward larger prefill/input checks. The next safe step is
one case at raw target `384`; the faster boundary check is raw target `512`.
Keep `--max-output-tokens 16`, force-stop/timeout diagnostics, and one-case
execution.

The follow-up raw target `384` and `512` checks also succeeded:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201022/summary.md
template=raw
target=384
final_input_chars_approx=768
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201139/summary.md
template=raw
target=512
final_input_chars_approx=1024
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language
```

With the dev-only bypass chain active, raw generated-filler inputs at
`final_input_chars_approx=768` and `1024` also reach NPU decode. The 512
sequential hypothesis is therefore not supported for this raw hidden-receiver
probe condition. The investigation then moved to larger raw-only
prefill/input checks with max output tokens at `16`, `--limit-cases 1`,
timeout/force-stop, and diagnostics collection.

The current script's largest built-in raw target, `640`, also succeeded:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201630/summary.md
prompt_transport=base64
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
template=raw
target=640
prompt_chars=1280
final_input_chars_approx=1280
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
control_chars=false
```

The larger raw-only targets `1024` and `2048` also succeeded with the dev-only
bypass chain active:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_202602/summary.md
template=raw
target=1024
final_input_chars_approx=2048
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_202732/summary.md
template=raw
target=2048
final_input_chars_approx=4096
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=64
sanitized_len=64
quality=natural_japanese
control_chars=false
```

Raw generated-filler input now reaches NPU decode through
`final_input_chars_approx=4096`. This confirms 4096-input-prefill-equivalent
native decode reachability under the hidden receiver, raw generated-filler,
max-output-16, dev-only bypass condition. The 512 sequential hypothesis is not
supported for this measured condition. This is not standard ChatScreen NPU
enablement and does not prove safety for the standard route.

Next topics should be separated: natural-language long-prompt checks near
4096, template-path echo/sanitizer behavior, safety gate redesign before any
standard route promotion, and output lengths above 512 or future 4096-output
work as separate investigations.

For the natural-language long-prompt follow-up, use `--prompt-file` rather than
putting the text directly on the `adb shell am broadcast` command line. The
script reads a UTF-8 text file, rejects empty/unreadable/NUL-containing input,
preserves newlines, and sends the content through the existing
`prompt_base64` transport. With `--prompt-file`, `target` is only a case label:
`prompt_chars` and `final_input_chars_approx` from the file content are the
source of truth. Treat generated-filler results and natural-language prompt
results as separate evidence classes. The 4096-near check should remain raw
template, hidden receiver only, dev-only prompt-length bypass, max-output `16`,
and `--limit-cases 1`.

The first prompt-file natural-language long-prompt check also reached NPU
decode:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_204237/summary.md
prompt_source=prompt_file
prompt_file=/tmp/lami_npu_prompt/ja_long_4096.txt
prompt_transport=base64
template=raw
target=2048
prompt_chars=3759
final_input_chars_approx=3759
native_pre_reject_expected_by_128_gate=true
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=25
sanitized_len=15
quality=mixed_language
control_chars=true
```

This shows that the 4096-near result is not limited to generated `x ` filler:
approximately 3759 Japanese natural-language prompt characters were delivered
through prompt-file/base64 transport and reached native decode under the same
dev-only hidden receiver bypass condition. Output quality remains a separate
issue because this run was classified as `mixed_language` with control
characters present.

Additional prompt-file quality comparisons reached native/decode but produced
empty native output:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_204816/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_short_answer_4096.txt
prompt_chars=5614
final_input_chars_approx=5614
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_205044/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_short_answer_3800.txt
prompt_chars=3754
final_input_chars_approx=3754
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_205211/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4104
final_input_chars_approx=4104
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output
```

The `20260529_205211/raw_2048` detail confirms this is not an NPU reachability
failure: native diag recorded `result=success`, prompt validation `ok`,
`prompt_input_code_points=4104`, `native_prompt_length_gate_bypassed=true`,
prefill reached, decode reached with `SetMaxOutputTokens(16)`,
`output_candidates=1`, and `output_bytes=0`. The receiver then classified the
case as `empty_after_sanitize` with raw and sanitized output length `0`.

Therefore these strict/loose short-answer prompt-file failures are an
empty-output/quality issue, not a 512 sequential or prefill boundary failure.
They should be separated from the generated-filler 4096 success and the earlier
natural prompt 3759 success. Next quality work should vary prompt tail
instructions, temperature/stop/eos behavior, max output tokens, and template
formatting as separate axes.

### Long Input / Natural Prompt Quality Matrix

All rows below are existing artifacts from the dev-only hidden receiver path
with prompt-length bypass enabled, `prompt_base64` transport, raw template, and
requested/effective max output tokens `16/16`.

| artifact | prompt_type | target | prompt/final input | status | native/decode | npu_evidence | requested/effective | output | quality | conclusion |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| `20260529_200054` | generated filler | 128 | `256/256` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | bypass chain reached NPU decode beyond 128 gate |
| `20260529_200533` | generated filler | 256 | `512/512` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | 512-ish input reached NPU decode |
| `20260529_201022` | generated filler | 384 | `768/768` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | larger prefill reached NPU decode |
| `20260529_201139` | generated filler | 512 | `1024/1024` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | 1024-ish input reached NPU decode |
| `20260529_201630` | generated filler | 640 | `1280/1280` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | script's previous largest target reached NPU decode |
| `20260529_202602` | generated filler | 1024 | `2048/2048` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 32 / sanitized 31 | mixed_language | 2048-ish input reached NPU decode |
| `20260529_202732` | generated filler | 2048 | `4096/4096` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 64 / sanitized 64 | natural_japanese | 4096-ish generated filler reached NPU decode |
| `20260529_204237` | natural prompt-file | 2048 | `3759/3759` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 25 / sanitized 15 | mixed_language | Japanese natural prompt near 4096 reached NPU decode |
| `20260529_204816` | strict short-answer prompt-file | 2048 | `5614/5614` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_205044` | strict short-answer prompt-file | 2048 | `3754/3754` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_205211` | loose greeting prompt-file | 2048 | `4104/4104` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | output_bytes 0 / raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_211227` | dialog-tail prompt-file | 2048 | `4422/4422` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | 16/16 | raw 31 / sanitized 30 | natural_japanese | dialog continuation tail restored output |

This matrix separates reachability from output quality. Raw generated filler
reaches NPU decode through `final_input_chars_approx=4096`, and a Japanese
natural prompt-file reaches decode at `prompt_chars=3759`. The strict/loose
short-answer prompt-file failures also reach native/decode, but native returns
empty output (`output_bytes=0` in the inspected `20260529_205211/raw_2048`
detail). Empty output is therefore not evidence for a 512 sequential or prefill
boundary. It is a prompt/output-quality axis. Do not promote this to standard
ChatScreen routing from these results; if runtime is expanded, change only one
axis per one-case run: prompt tail instruction, template, or max output tokens.

### Prompt Tail / Native Output Metadata Comparison

The table below compares existing artifacts only. No additional runtime was
executed for this classification.

| artifact | case | prompt tail pattern | prompt code points | bytes | gate bypass | max output | native output | timing | receiver output | stop/reason | conclusion |
| --- | --- | --- | ---: | ---: | --- | --- | --- | --- | --- | --- | --- |
| `20260529_202732/raw_2048` | generated filler 4096 success | tail remains repeated `x ` filler | 4095 | actual/prompt 4095 | true | `SetMaxOutputTokens(16)` | candidates 1, bytes 192 | prefill 267 ms, decode 434 ms | raw 64, sanitized 64, natural_japanese | stop blank, reason success | reaches decode and returns non-empty output |
| `20260529_204237/raw_2048` | natural prompt 3759 success | repeated Japanese natural sentence ending with "短く返答してください。" | 3759 | actual/prompt 9519 | true | `SetMaxOutputTokens(16)` | candidates 1, bytes 51 | prefill 476 ms, decode 373 ms | raw 25, sanitized 15, mixed_language | stop blank, reason success | natural long prompt reaches decode and returns output |
| `20260529_205044/raw_2048` | strict short-answer 3754 empty | repeated context plus final instruction: "日本語で「こんにちは」と一言だけ返答してください。" | 3754 | actual/prompt 9934 | true | `SetMaxOutputTokens(16)` | candidates 1, bytes 0 | prefill 205 ms, decode 22 ms | raw 0, sanitized 0, empty_output | receiver stop empty_after_sanitize | decode succeeds but native output is empty |
| `20260529_205211/raw_2048` | loose greeting 4104 empty | repeated natural sentence plus final instruction: "日本語で短く挨拶してください。" | 4104 | actual/prompt 10544 | true | `SetMaxOutputTokens(16)` | candidates 1, bytes 0 | prefill 322 ms, decode 22 ms | raw 0, sanitized 0, empty_output | receiver stop empty_after_sanitize | decode succeeds but native output is empty |
| `20260529_211227/raw_2048` | dialog-tail 4422 success | conversation continuation tail: "ユーザー: こんにちは。" then "アシスタント:" | 4422 | not re-extracted | true | `SetMaxOutputTokens(16)` | not re-extracted | not re-extracted | raw 31, sanitized 30, natural_japanese | reason success | prompt-tail change restores non-empty output |

The common path across all four cases is successful prompt validation,
effective native prompt-length bypass, prefill, and RunDecode with
`SetMaxOutputTokens(16)`. The split is after decode: the two successful cases
return non-empty native output bytes, while the strict/loose short-answer
prompt tails return `output_candidates=1` with `output_bytes=0`. That makes the
likely variables prompt-tail shape, repeated prompt structure, stop/eos
behavior, and the low max-output cap rather than NPU reachability or a 512
sequential/prefill boundary. The next runtime comparison should be one case
only and change one axis, for example `max_output_tokens=32`, with all other
conditions held fixed.

That one-axis max-output comparison was run for the loose greeting prompt:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_210719/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4104
final_input_chars_approx=4104
requested/effective=32/32
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
raw_len=0
sanitized_len=0
quality=empty_output
```

Increasing the cap from `16` to `32` did not change the empty-output behavior
for this loose natural prompt. That weakens the hypothesis that the cap of 16
alone caused the empty output. Keep this classified as prompt-tail,
stop/eos/template, or model-output behavior rather than a prefill boundary. If
runtime expands again, change prompt tail only and keep max output fixed.

The prompt-tail-only comparison restored output:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_211227/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_dialog_tail_3800.txt
prompt_chars=4422
final_input_chars_approx=4422
prompt_tail=ユーザー: こんにちは。 / アシスタント:
requested/effective=16/16
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
raw_len=31
sanitized_len=30
quality=natural_japanese
control_chars=false
```

Changing only the natural prompt tail from a closed instruction style such as
"最後の指示: ... 一言だけ返答してください" to a dialog continuation style
(`ユーザー: ...` / `アシスタント:`) moved the same long-input class back from
empty output to `natural_japanese` success with max output still fixed at `16`.
Long prefill length itself is therefore unlikely to be the primary cause of
the empty-output cases. Standard-route promotion should not use raw string
injection directly; template and tail design need separate treatment.

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
- 1024/2048/4096 are only demonstrated under the dev-only hidden receiver,
  raw generated-filler, prompt-length-bypass, max-output-16 probe condition;
  they remain outside standard ChatScreen routing.

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
