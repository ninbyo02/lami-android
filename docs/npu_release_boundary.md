# NPU Release Boundary

## Current status

The Qualcomm NPU path is a development preview. Two parts are verified separately:

1. SM8750 hardware generation succeeds in the isolated `customBuildExperimentDebug` flavor.
2. Standard-route contracts exist for finalized safe text, UI append, DB persistence,
   Markdown rendering, pseudo streaming, TTS, cancellation, and kill-switch behavior.

The real QAIRT 2.44 provider now compiles in the Standard source set. An explicitly
enabled Standard Debug candidate passes the production persistent provider on SM8750,
but the equivalent Standard Release candidate crashes during NPU engine creation.
Normal Release builds still exclude vendor runtime binaries. Therefore, the distributed
Standard Release APK must not yet be described as NPU-enabled.

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

The Standard Release candidate remains a diagnostic build and is not promotion evidence:

```bash
./gradlew assembleStandardRelease -Plami.standardNpuRuntimeEnabled=true
scripts/tests/test_standard_release_npu_packaging.sh \
  app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk \
  enabled app/src/customBuildExperimentDebug/jniLibs/arm64-v8a
```

Omitting the Gradle property produces the distributable Standard Release shape and the
packaging check must pass in `disabled` mode. Enabling it also changes the application ID
to `io.github.ninbyo02.lami.npuvalidation`, preventing a locally signed validation APK
from replacing or deleting data from the installed product app. The property is a
validation gate, not a redistribution approval.

## Known Standard Release blocker

On nubia NX733J / SM8750, the locally signed Standard Release candidate reaches
`EngineFactory::CreateDefault` and then crashes in
`DispatchDelegate::CreateDelegateKernelInterface`. The same model, signing certificate,
package data, and byte-identical common native libraries pass under Standard Debug.
Changing package length, native extraction, debuggability, `liblitertlm_jni.so`, and the
persistent-holder stub did not remove the Release-only crash. Evidence is stored under
`artifacts/standard_npu_release_device_validation/20260827_202200_f56a4339`.

Until this build-type boundary is resolved, Standard Debug evidence must not be relabeled
as Standard Release evidence and PR #2542 should remain Draft.

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
