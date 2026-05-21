# QAIRT 2.44 Dispatch Logging Root Cause Update

Date: 2026-05-21

## Summary

The next diagnostic layer is prepared but not yet executed on device.

A detailed logging build of the QAIRT 2.44 custom stack was produced at:

```text
artifacts/qairt244_dispatch_logging_build/20260521_085251/
```

The build includes `QAIRT244_DIAG` logs in LiteRT dispatch discovery/loading,
Qualcomm dispatch initialization, QNN manager setup, QNN library loading,
`ADSP_LIBRARY_PATH`, compatibility checks, and `has_dispatch_runtime`
transitions.

No `Engine.initialize` dry-run was executed in this pass because no adb device
was connected. Therefore no new dispatch/QNN runtime log result exists yet.

## What Changed

Diagnostic logs were added in the LiteRT source used by the LiteRT-LM Bazel
build. The produced libraries have new Build IDs:

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `04b7b85497a519e131777b55e6c9b456` |
| `libLiteRtDispatch_Qualcomm.so` | `50f4dbc09b133acb5973747555f06bc1` |
| `liblitertlm_jni.so` | `30ee8163ec17e1624a25f6936a163f9e` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `da4a7a69d0a36ad68a6dd10e6c183d62` |

The custom probe guard now accepts these logging Build IDs in addition to the
previous exact qairt244 Build IDs, so a future diagnostic dry-run will not stop
at `custom-stack-build-id-mismatch`.

## Current Matrix Update

| Hypothesis | Update | Confidence |
| --- | --- | --- |
| H1 SM8750/V79 dispatch capability mismatch | Logging is now available around capabilities and HTP init, but no device result yet. | medium |
| H2 Android namespace/path issue | Preflight adb collection could not run because no device was connected. | medium |
| H3 QNN/HTP/skel load issue | Logging now covers QNN `dlopen`, provider `dlsym`, `ADSP_LIBRARY_PATH`, and HTP init. Result pending dry-run. | medium |
| H4 model schema mismatch | Still lower priority because model carries QAIRT 2.44, SM8750, V79, and dispatch markers. | low-medium |
| H5 dispatch runtime registration/check failure | Still the leading class. The new logs are designed to split dispatch `.so` discovery, `LiteRtDispatchGetApi`, compatibility, QNN manager, and device context creation. | high |
| H6 CLI vs Android app difference | CLI target remains design-only. Upstream `litert_lm_main` remains unsafe because it generates. | unknown |

## Next Single Step

Run exactly one `customBuildExperimentDebug` explicit `Engine.initialize`
dry-run with:

```text
artifacts/qairt244_dispatch_logging_build/20260521_085251/
```

Only do this when an adb device is connected. The dry-run should collect:

- logcat lines containing `QAIRT244_DIAG`
- stage file
- probe snapshot
- tombstone/dropbox if the process aborts
- mapped library matrix
- rootless QNN/CDSP path properties

Do not run prompt generation, `Conversation`, `Session`, `generateResponse`, or
single-token smoke.
