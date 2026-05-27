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
native entry.

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

2. If runtime probing is approved, run exactly one 30-case hidden matrix,
   understanding that current 512/640 final-input rows are expected to reject
   before native because of the 128-codepoint gate:

```bash
scripts/run_npu_512_sequence_probe.sh --execute --timeout 60 --max-output-tokens 16
```

3. If direct 512 graph/prefill boundary evidence is still required, design a
   separately approved dev-only validation bypass that is non-ChatScreen,
   non-persistent, does not connect DB/TTS/Markdown/streaming, and does not
   hide fallback.

If all targets above the existing 128-codepoint hidden route gate reject before
native entry, the next safe design step is a dev-only, non-ChatScreen,
non-persistent validation bypass dedicated to prefill-length probing. That
would be a separate approval because it changes app-side guard behavior, even
if it remains hidden-only.
