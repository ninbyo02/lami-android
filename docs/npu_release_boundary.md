# NPU Release Boundary

## Current status

The Qualcomm NPU path is a development preview. Two parts are verified separately:

1. SM8750 hardware generation succeeds in the isolated `customBuildExperimentDebug` flavor.
2. Standard-route contracts exist for finalized safe text, UI append, DB persistence,
   Markdown rendering, pseudo streaming, TTS, cancellation, and kill-switch behavior.

The real QAIRT 2.44 provider now compiles in the Standard source set. Explicitly
enabled Standard Debug and local Standard Release validation candidates both pass the
production persistent provider on SM8750. Normal Release builds still exclude vendor
runtime binaries, and the Release validation receiver/runtime is property-gated.
Therefore, the distributed Standard Release APK must not yet be described as NPU-enabled.

## Verified device evidence

- Device: nubia NX733J
- SoC: SM8750
- DSP: Hexagon V79
- Backend evidence: `QNN_HTP_V79_FastRPC_native_diag`
- Sampler: top-k 40, top-p 0.9, temperature 0.3, seed 42
- Two-turn outputs: `東京`, then `日本`
- Custom Debug validation artifact: `20260826_230151_700539701`
- Standard Debug persistent-route artifact: `20260827_215704_290186100`
- Standard Debug native input sizes: 57 and 105 code points (limit: 128)
- Standard Release API preflight: `20260828_234533_342986162`
- Standard Release initialize preflight: `20260828_234552_696772357`
- Standard Release two-turn artifact: `20260828_234613_782382036`
- Standard Release APK SHA-256: `001fb55576d7f20160962772c67efe7156510dc1303abcc5deddb26439636bea`
- Standard Release native input sizes: 60 and 105 code points (limit: 128)

## Local Standard candidate builds

Validation candidates may be built only on a licensed workstation that supplies
QAIRT/QNN libraries outside Git. The Standard Debug candidate is the currently verified
persistent-route device shape:

```bash
./gradlew assembleStandardDebug -Plami.standardNpuRuntimeEnabled=true
scripts/run_npu_conversation_policy_device_validation.sh \
  --endpoint <IPv4:port> \
  --apk app/build/outputs/apk/standard/debug/app-standard-debug.apk \
  --app-id io.github.ninbyo02.lami.npuvalidation \
  --skip-artifact-verification
```

The Standard Release candidate now passes its isolated device gates, but remains a
local validation build rather than a redistributable production artifact:

```bash
./gradlew assembleStandardRelease -Plami.standardNpuRuntimeEnabled=true
scripts/tests/test_standard_release_npu_packaging.sh \
  app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk \
  enabled app/src/customBuildExperimentDebug/jniLibs/arm64-v8a
```

After local signing, run the isolated gates in order:

```bash
scripts/run_standard_npu_release_preflight.sh \
  --endpoint <IPv4:port> --apk <signed-validation.apk> \
  --mode dispatch_api_preflight
scripts/run_standard_npu_release_preflight.sh \
  --endpoint <IPv4:port> --apk <signed-validation.apk> \
  --mode dispatch_initialize_preflight
scripts/run_standard_npu_release_conversation_validation.sh \
  --endpoint <IPv4:port> --apk <signed-validation.apk>
```

The preflight receiver is present only in an explicitly enabled Standard Release and
runs in `:npu_preflight` under the shell-only `android.permission.DUMP` gate.

Omitting the Gradle property produces the distributable Standard Release shape and the
packaging check must pass in `disabled` mode. Enabling it also changes the application ID
to `io.github.ninbyo02.lami.npuvalidation`, preventing a locally signed validation APK
from replacing or deleting data from the installed product app. The property is a
validation gate, not a redistribution approval.

## Resolved Standard Release blocker

The earlier Release-only abort was reproduced under
`artifacts/standard_npu_release_device_validation/20260827_202200_f56a4339` and
`20260828_213952_495766796`. Optional `libcdsprpc.so` visibility was disproven as the
root cause. The decisive preflight showed that the enabled Release APK used direct APK
native loading while LiteRT-LM received `ApplicationInfo.nativeLibraryDir`; the exact
Dispatch path therefore did not exist on the filesystem.

The explicit local validation variant now enables legacy JNI extraction only when
`lami.standardNpuRuntimeEnabled=true`. Normal Standard Release keeps its original
non-vendor packaging. The API preflight then reached the packaged Qualcomm Dispatch
library and returned API version `0.1.0`, status 0, and a present interface. A second
validation bug incorrectly required a major version above zero; it now accepts valid
non-zero pre-1.0 versions.

On SM8750, both isolated stages now pass before LLM engine creation. Qualcomm Dispatch
initializes successfully, reports capabilities 1, and creates/destroys its device context.
The exact same signed APK then passes the production persistent route for `東京` and
`日本`, with NPU sampler evidence, no fallback or timeout, and 60/105 code-point inputs.
Post-install `stopped=true` is cleared once before the base process is killed, so the
permission-guarded receiver still cold-starts in `:npu_preflight` and preserves evidence
outside the process.

Property-switch contamination is also guarded: the Release JNI merge is always refreshed,
and the disabled packaging check rejects a staged custom LiteRT core. Normal Standard
Release does not request `libcdsprpc.so`; only Standard Debug and the explicitly enabled
local validation Release declare it. PR #2542 remains Draft until the remaining Standard
promotion gates, especially combined product UI/DB/TTS/Markdown and lifecycle coverage,
are satisfied.

## Distribution boundary

Qualcomm/QAIRT/QNN runtime binaries are local SDK inputs. They must not be committed
to Git or distributed in an APK until their redistribution terms are explicitly approved.

## Standard promotion gates

All gates are required before changing the status to NPU Beta or production candidate:

- reproducible native build from pinned LiteRT-LM source and externally supplied SDK inputs;
- no tracked vendor `.so`, `.aar`, APK, or SDK archive;
- real NPU provider compiled into the intended Standard variant;
- Release-equivalent device run proving NPU evidence and no CPU/GPU fallback;
- combined UI, DB, Markdown, pseudo-streaming, TTS, cancellation, and lifecycle validation;
- cold-start and repeated multi-turn stability coverage;
- standard debug and release CI green on JDK 21;
- kill switch and compatibility failure path retained;
- README and release notes updated from evidence produced by that exact build.

## Terminology

- **NPU development preview**: the current state.
- **NPU Beta**: allowed only after every Standard promotion gate passes.
- **NPU production candidate**: requires expanded device coverage beyond the single
  SM8750 validation device.
