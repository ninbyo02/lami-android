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
| 2. QAIRT 2.42 comparison / downgrade experiment | Matches Radxa public Linux docs; may expose stable public QNN generation behavior and V68/V73 assumptions. | Older than LiteRT public metadata; Radxa flow is Linux SBC oriented; not documented for SM8750/V79; likely unsuitable as the primary Gemma 4 SM8750 Android generation. | medium | low |
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
5. Use QAIRT `2.42.0.251225` as a static comparison baseline if it becomes
   available; do not treat it as the primary SM8750/V79 candidate without
   maintainer or vendor evidence.
6. In parallel, ask maintainers whether QAIRT `2.42`, `2.44`, `2.46`, or Gallery
   internal payload is the intended generation for SM8750/V79 Android
   `Backend.NPU`.

## QAIRT 2.42 Position

Radxa public documentation makes QAIRT `2.42.0.251225` a useful public
Qualcomm ecosystem baseline. It is lower priority than exact QAIRT 2.44 for the
current LiteRT source because:

- LiteRT public Qualcomm metadata references QAIRT `2.44.0.260225`.
- The local available SDK is QAIRT `2.46.0.260424`.
- Radxa's documented public flows focus on Linux boards and V68/V73 examples.
- Lami's target is Android app `Backend.NPU(nativeLibraryDir)` on SM8750/V79.

If QAIRT 2.42 is obtained, first run only:

```bash
bash scripts/check_qairt242_sdk.sh \
  /home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225

bash scripts/compare_qairt_generations.sh \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225
```

No build or app insertion should follow from 2.42 without a separate decision.

## Do Not Do Yet

- no normal UI NPU path
- no `selectedPath=npu`
- no `Conversation`
- no `Session`
- no `generateResponse`
- no native library changes outside an explicitly approved isolated experiment
- no app insertion without static comparison and explicit approval
