# LiteRT / QAIRT Generation Strategy Options

Date: 2026-05-17

## Current State

`customBuildExperimentDebug` with same-source/tag LiteRT-LM `v0.11.0` and
pinned LiteRT still fails during `Engine.initialize`:

```text
DispatchDelegate::CreateDelegateKernelInterface()+312
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

No NPU generation has been run.

## Strategy Options

| Option | Pros | Cons / Risks | Effort | Confidence for reaching `Engine.initialize` success |
| --- | --- | --- | --- | --- |
| 1. QAIRT 2.44 exact acquisition | Matches public LiteRT metadata; removes known 2.46-over-2.44 overlay risk. | SDK not local; requires Qualcomm/QPM access; may still fail if SM8750 requires internal payload. | medium | medium-high |
| 2. QAIRT 2.42 downgrade experiment | Matches Radxa public Linux docs; may expose stable public QNN generation behavior. | Older than LiteRT public metadata; not documented for SM8750/V79; likely unsuitable for Gemma 4 SM8750 model. | medium | low-medium |
| 3. Official issue escalation | Gets maintainer guidance on required artifact generation, QAIRT version, and Android packaging. | Blocks on external response; may require sharing more artifacts. | low | high for choosing the right next path |
| 4. Vendor/system-provided QNN libs usage | May align with device vendor runtime and Android linker behavior. | Hard in app sandbox; licensing/redistribution concerns; may not match LiteRT dispatch build. | high | medium |
| 5. customBuildExperiment with explicit QNN libs packaging | Tests whether packaged QNN/HTP/skel/stub discovery is the immediate blocker. | Could mix incompatible generations; must remain isolated; licensing review required. | medium | medium |
| 6. same-source `litert_lm_main` CLI NPU proof | Separates Android app packaging from LiteRT-LM/model/runtime compatibility. | Requires CLI build and device staging; still no generation beyond controlled initialize unless approved. | high | medium |
| 7. Android vendor path investigation | Clarifies linker namespace, ADSP/CDSP, and fastrpc differences from Linux. | May require platform-specific tooling/root/log access; does not solve generation mismatch alone. | medium | medium |
| 8. abandon current public stack path | Avoids spending time on a likely unsupported public path. | Leaves NPU path unresolved; depends on upstream/Gallery release. | low | n/a |

## Recommended Order

1. Post or prepare the official issue with the latest QAIRT findings.
2. Acquire exact QAIRT `2.44.0.260225` if possible.
3. Run `scripts/run_qairt244_rebuild_compare.sh` after acquisition.
4. If qairt244 static compare looks coherent, design a later isolated insertion
   and `Engine.initialize` dry-run only.
5. In parallel, ask maintainers whether QAIRT `2.42`, `2.44`, `2.46`, or Gallery
   internal payload is the intended generation for SM8750/V79 Android
   `Backend.NPU`.

## Do Not Do Yet

- no normal UI NPU path
- no `selectedPath=npu`
- no `Conversation`
- no `Session`
- no `generateResponse`
- no native library changes outside an explicitly approved isolated experiment
- no app insertion without static comparison and explicit approval
