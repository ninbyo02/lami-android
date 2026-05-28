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

Current evidence from the instrumented worker runtime shows prompt 2 reaches
`before_native_adapter_run`, native diagnostics reach
`before RunDecode SetMaxOutputTokens(512)`, and Kotlin does not regain
control. That narrows the active 512 sequential blocker to native
non-return/process death under sequential reuse. The real-path `.litertlm`
static scan comparison is now complete and did not find SM8750-specific
readable metadata proving `512` sequence/prefill/context/input length.

Current conclusion: the 512 sequential hypothesis is not supported by static
scan evidence, but it remains unclosed. The existence of a compiled graph
shape limit still has to be checked with runtime evidence at the final-input
boundary. The existing hidden route cannot directly test 512/640 final-input
rows because `HIDDEN_TEMPLATE_MAX_LENGTH=128` rejects those cases before
native entry. The first safe runtime row also shows that max-output-token
propagation/output quality must be understood before broader sequential
probing.

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

3. Before any broader hidden matrix run, replace or rebuild the dev-only
   native artifact if the goal is to verify `--max-output-tokens 16`
   propagation. The installed APK library and local build artifact both
   contain the max512 marker path.

4. If direct 512 graph/prefill boundary evidence is still required, design a
   separately approved dev-only validation bypass that is non-ChatScreen,
   non-persistent, does not connect DB/TTS/Markdown/streaming, and does not
   hide fallback.

If all targets above the existing 128-codepoint hidden route gate reject before
native entry, the next safe design step is a dev-only, non-ChatScreen,
non-persistent validation bypass dedicated to prefill-length probing. That
would be a separate approval because it changes app-side guard behavior, even
if it remains hidden-only.
