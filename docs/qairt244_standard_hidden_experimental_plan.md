# QAIRT244 Standard Hidden Experimental Plan

## Purpose

Move the proven qairt244 SM8750 NPU route from `customBuildExperimentDebug`
toward `standardDebug` as a hidden experimental path. This is not production
`Backend.NPU` enablement. It is a controlled developer-only bridge so
`./update.sh update`, which installs `standardDebug`, can exercise the same
bounded route without exposing it to normal users.

Current evidence from `customBuildExperimentDebug`:

- 128-token UTF-8 `internal_intent` Japanese prompts passed 3/3:
  `こんにちは`, `テスト`, `ラミィ`
- `max_output_tokens=128`
- `native_max_output_tokens_limit=128`
- `prompt_validation_mode=utf8_internal_intent`
- `native_prompt_validation_mode=utf8_internal_intent`
- `utf8_allowed=true`
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- UI cleanup succeeded

## Migration Scope

Move only the guarded qairt244 SM8750 route surface needed for a hidden
`standardDebug` experiment:

- model basename resolver and SM8750-only guard
- timestamp-prefixed SM8750 basename allowance
- 128-token bounded route constants
- ChatScreen route branch behind hidden gates
- Settings row visible only after developer access is enabled
- diagnostics for model basename, canonical basename, timestamp-prefix status,
  native token limit, RunDecode reachability, NPU evidence, fallback state, and
  UI cleanup

Do not move this into the normal selected-path NPU route. Do not expose generic
NPU candidate selection. Do not add automatic fallback in this phase.

## Step 2: Shared Logic Migrated

The first foundation step is to move only pure shared logic into the main
source set so `standardDebug` can compile it while still keeping the route
hidden and OFF:

- `Qairt244ModelPathResolver`: canonical/timestamp-prefixed SM8750 basename
  detection, qcs8275/generic/E2B/E4B rejection, candidate resolution, and model
  diagnostics fields
- `NpuDiagnosticPromptValidator`: ASCII diagnostic and UTF-8 internal-intent
  prompt validation, including empty/NUL/control/invalid UTF-8/32-code-point
  rejection

The following remain `customBuildExperimentDebug`-only until the hidden
standard route is implemented:

- `Qairt244DevOnlyNpuRouteAdapter`
- `DevQairt244PromptReceiver`
- `Qairt244ShortMultitokenSmoke` and other native smoke entrypoints
- custom manifest receiver/activity declarations
- native custom artifact packaging
- ChatScreen reflection entrypoint activation
- Settings visibility for the qairt244 NPU toggle

This step creates no standard user-visible UI, no standard ChatScreen NPU
execution branch, no production `Backend.NPU` promotion, and no automatic
fallback.

## Hidden Gate Options

The hidden route should require two layers:

1. A developer access gate that reveals experimental settings.
2. A qairt244 SM8750 route toggle that is default OFF.

Acceptable developer access gates:

- Version tap sequence: repeated taps on the app version or build row unlock
  developer settings locally.
- ADB flag: an app-private debug flag written by `adb shell run-as` for
  deterministic test setup.
- Developer setting: a persisted developer-settings switch after the hidden
  area is unlocked.

Recommended first implementation: support an ADB flag for automation and a
version tap sequence for manual testing. Both should only reveal the hidden
experimental Settings row; neither should enable NPU by itself.

## Settings Display

Normal users must not see any qairt244 NPU control in `standardDebug`.

Current Step 3 plumbing:

- Developer access is local to DEBUG builds and is enabled by tapping the About
  screen version/build text seven times.
- With developer access OFF, `standardDebug` shows no qairt244 NPU Settings row.
- With developer access ON, `standardDebug` shows a disabled/read-only
  `実験的NPU（SM8750）` row that says `standard hidden experimental`, `まだ本適用
  ではありません`, and `ChatScreen route activation は次ステップ`.
- The row is display-only. It does not write the qairt244 route toggle and does
  not activate ChatScreen execution.
- `customBuildExperimentDebug` keeps its existing `DEV: SM8750 NPU実験` toggle
  and does not show the standard hidden placeholder row, avoiding duplicate
  controls.
- The next step is ChatScreen hidden route activation behind the standard gate
  and SM8750 model guard.

When developer access is enabled, Settings may show a hidden experimental row:

- Label: `Experimental: SM8750 NPU`
- Default: OFF
- Enabled only when the app detects exactly one acceptable SM8750 model:
  `gemma-4-E2B-it_qualcomm_sm8750.litertlm` or
  `<digits>_gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- Disabled with an explicit reason when the model is absent, ambiguous, generic,
  qcs8275, E4B, plain E2B, or non-numeric-prefixed
- Text must say this is hidden, experimental, SM8750-only, and not production
  NPU support

## ChatScreen Prompt Policy

The `customBuildExperimentDebug` evidence uses `internal_intent` for Japanese
UTF-8 prompts because shell text input is not reliable for non-ASCII. For
`standardDebug`, ChatScreen normal input must eventually support user-entered
Japanese directly through the UI path, not through the internal intent receiver.

Initial `standardDebug` acceptance can use ASCII UI prompts for the hidden
route, but Japanese ChatScreen normal-input support is required before any
broader exposure. The prompt validator must keep rejecting empty prompts, NUL,
invalid UTF-8, and prompts above 32 UTF-8 code points unless a later design
explicitly raises that prompt bound.

## Toggle Persistence

Do not automatically turn the NPU toggle OFF after each conversation in
`standardDebug`. The custom experiment auto-clear behavior was useful for
guarded one-shot runs, but a hidden experimental standard path should let the
developer keep the route enabled across repeated manual checks.

The route still needs duplicate-run protection and must clear ChatScreen
responding/stop UI state after success, failure, timeout, and exception.

## Failure UX

On failure, do not silently fallback. Show an explicit developer-facing error in
the ChatScreen conversation or a hidden diagnostics surface:

```text
Experimental SM8750 NPU failed: <reason>
```

Diagnostics should include:

- failure stage
- resolved model basename
- canonical model basename
- timestamp prefix stripped true/false
- native token limit
- RunDecode reached true/false
- NPU evidence
- fallback used false
- timeout/fresh crash state
- UI cleanup result

Manual GPU recovery should be obvious: turn the hidden NPU toggle OFF and retry
with the existing GPU/CPU local route.

## Fallback Policy

For the hidden `standardDebug` phase:

- no automatic NPU -> GPU -> CPU fallback
- no silent retry on GPU/CPU
- no persistence that makes a failed NPU attempt look like a normal local
  inference success

Future work may design explicit NPU -> GPU -> CPU fallback, but only after the
diagnostics can report which backend produced the final answer and after the
failure UX is reviewed.

## StandardDebug Acceptance Gates

The hidden `standardDebug` route passes only if all of these are true:

- hidden developer access required before the Settings row is visible
- qairt244 SM8750 toggle defaults OFF
- canonical and numeric timestamp-prefixed SM8750 model basenames are accepted
- qcs8275, generic, plain E2B, E4B, and non-numeric-prefixed files are rejected
- `max_output_tokens=128`
- `native_max_output_tokens_limit=128`
- ChatScreen normal input run succeeds at least once with `Hello`
- Japanese normal-input plan is documented before broader exposure
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- duplicate run is blocked
- UI cleanup succeeds with no stale `Responding...`, Stop button, or `応答中`
- route remains hidden and disabled for normal users

## Conditions That Must Not Reach Standard Users

Do not show or enable the route in normal Settings when:

- developer access is disabled
- the model is missing or ambiguous
- only qcs8275, generic, E4B, or plain E2B models are present
- the native artifact does not report the 128-token guard
- NPU evidence is absent or ambiguous
- fallback behavior is automatic or silent
- UI cleanup is unreliable
- the implementation raises token limits above 128
- the route is wired into normal selected-path NPU candidate selection
- packaging or update behavior would add `.so`, `.apk`, `.aar`, `.zip`,
  `.tar`, `.gz`, or `.litertlm` artifacts to Git

The next implementation step is Settings cleanup and hidden-gate plumbing for
`standardDebug`; it should not change native artifacts, token limits, or
fallback behavior.
