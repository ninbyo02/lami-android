# NPU Release Boundary

## Current status

The Qualcomm NPU path is a development preview. Two parts are verified separately:

1. SM8750 hardware generation succeeds in the isolated `customBuildExperimentDebug` flavor.
2. Standard-route contracts exist for finalized safe text, UI append, DB persistence,
   Markdown rendering, pseudo streaming, TTS, cancellation, and kill-switch behavior.

These parts have not yet passed a combined Standard Release device validation.
The real QAIRT 2.44 provider remains under the debug source set, so the Standard Release
APK must not be described as NPU-enabled.

## Verified device evidence

- Device: nubia NX733J
- SoC: SM8750
- DSP: Hexagon V79
- Backend evidence: `QNN_HTP_V79_FastRPC_native_diag`
- Sampler: top-k 40, top-p 0.9, temperature 0.3, seed 42
- Two-turn outputs: `東京`, then `日本`
- Validation artifact: `20260826_230151_700539701`

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
