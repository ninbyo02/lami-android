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

The current known-good native artifact is still one-token-only. Before a run is
allowed, a new LiteRT-LM JNI artifact must include both:

```text
qairt244_short_multitoken_smoke_v1
DecodeConfig.SetMaxOutputTokens(3)
```

The runner blocks execution unless static scan finds this evidence in the
external LiteRT-LM checkout or supplied custom artifact metadata.

## Runner

Script:

```text
scripts/run_qairt244_short_multitoken_smoke.sh
```

Default behavior is preflight-only:

```bash
bash scripts/run_qairt244_short_multitoken_smoke.sh
```

Future execution command, only after a rebuilt artifact exists:

```bash
bash scripts/run_qairt244_short_multitoken_smoke.sh \
  --artifact artifacts/<qairt244_short_multitoken_build> \
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

