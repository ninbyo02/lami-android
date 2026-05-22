# QAIRT 2.44 Short Multi-Token Smoke Plan

Date: 2026-05-23

Scope: `customBuildExperimentDebug` isolated lower-level NPU smoke with
`maxOutputTokens=3`. This is not normal UI integration.

## Goal

Move one step beyond the proven one-token smoke by preparing a bounded
three-token smoke:

- prompt fixed to `Hi`
- hard cap `maxOutputTokens=3`
- lower-level LiteRT-LM path only
- no high-level `generateResponse`
- no streaming generation
- no normal `ChatScreen` route
- no `selectedPath=npu` normal path

## App-Side Skeleton

Added `customBuildExperimentDebug`-only wrappers:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmokeActivity.kt
```

Manifest entry:

```text
io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmokeActivity
```

The Activity requires:

```text
runShortMultitokenSmoke=true
```

and writes:

```text
files/qairt244_short_multitoken_smoke_result.txt
files/qairt244_native_diag.txt
```

The wrapper intentionally stays in `customBuildExperimentDebug`. It is not
available from standard, galleryStackExperiment, npuExperiment, or release.

## Native Requirement

A QAIRT 2.44 short multi-token native artifact was built at:

```text
artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/
```

It includes both:

```text
qairt244_short_multitoken_smoke_v1
DecodeConfig.SetMaxOutputTokens(3)
```

The runner blocks execution unless static scan finds this evidence in the
external LiteRT-LM checkout and the supplied custom artifact metadata. For
execution, the supplied artifact must also contain the marker in
`built_libs/liblitertlm_jni.so`.

## Runner

Script:

```text
scripts/run_qairt244_short_multitoken_smoke.sh
```

Default behavior is preflight-only:

```bash
bash scripts/run_qairt244_short_multitoken_smoke.sh
```

Execution command, used once for the recorded smoke:

```bash
bash scripts/run_qairt244_short_multitoken_smoke.sh \
  --artifact artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526 \
  --run
```

## Safety Gates

- `customBuildExperimentDebug` only
- prompt fixed to `Hi`
- `maxOutputTokens=3`
- timeout default `30` seconds
- explicit `--run` required
- custom native artifact required
- native marker required
- `SetMaxOutputTokens(3)` static evidence required
- no normal UI path
- no high-level `generateResponse`
- no streaming
- no automatic fallback to GPU/CPU chat path

## Execution Policy

Execution is maximum one run after all static gates pass. If any gate fails,
the script creates a blocked preflight artifact and exits without generation.

## Recorded Run

Artifact:

```text
artifacts/qairt244_short_multitoken_smoke/20260523_075743/
```

Result:

```text
result=success
output=! How Hi
max_output_tokens=3
elapsed_ms=1358
decode_elapsed_ms=164
tombstone_classification=stale-tombstone-ignored
```
