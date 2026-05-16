# QAIRT 2.44.0.260225 Acquisition Notes

Date: 2026-05-17

## Current Status

QAIRT `2.44.0.260225` was not found as a real local SDK install.

The only local path matching that version is the investigation overlay:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

That path is a symlink to the existing QAIRT `2.46.0.260424` SDK:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

Search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

No exact QAIRT `2.44.0.260225` rebuild was performed.

## Required SDK

The LiteRT source pinned by LiteRT-LM `v0.11.0` expects:

```text
QAIRT 2.44.0.260225
```

Acquire it only from Qualcomm official distribution channels, such as Qualcomm
Package Manager / QPM or the official Qualcomm AI Runtime SDK download path
available to the developer account.

Likely requirements:

- Qualcomm ID / developer account
- acceptance of Qualcomm SDK license terms
- QPM or official SDK package access

Typical QPM install paths may look like:

```text
/opt/qcom/aistack/qairt/<version>
/opt/qcom/aistack/qairt/2.44.0.260225
```

## Local QPM / Software Center Probe

Probe date: 2026-05-17

Artifact:

```text
artifacts/qairt244_acquisition/20260517_074537/
```

Checked commands:

```text
qpm
qpm-cli
qualcomm-package-manager
software-center
```

Result:

- no QPM or Qualcomm Software Center CLI was found on this machine
- no `/opt/qcom/aistack/qairt/` install was found
- local search found the existing QAIRT `2.46.0.260424` SDK, but no real `2.44.0.260225` SDK
- `qpm search` could not be run because QPM CLI is not installed

This means acquisition currently requires one of:

1. install/use Qualcomm Package Manager with a Qualcomm account and accepted SDK license terms;
2. download the official QAIRT `2.44.0.260225` SDK package from Qualcomm Software Center;
3. provide an already extracted official SDK directory.

Do not substitute arbitrary QNN libraries from another device or APK as an "exact" SDK.
The existing 2.46 symlink overlay is not acceptable for an exact-match QAIRT
2.44 rebuild.

QPM workflow docs:

```text
docs/qpm_installation_notes.md
docs/qpm_qairt244_install_workflow.md
scripts/qpm_search_qairt_versions.sh
```

Current QPM search status:

```text
artifacts/qpm_search/20260517_085749/
```

`qpm-cli` is still missing, so the QAIRT catalog has not yet been queried.

## QPM / Software Center Search Guidance

Search terms to use in Qualcomm Package Manager or Qualcomm Software Center:

```text
QAIRT
Qualcomm AI Runtime
Qualcomm AI Runtime SDK
QNN SDK
2.44.0.260225
260225
```

Expected package/file name patterns may include:

```text
Qualcomm_AI_Runtime*
Qualcomm_AI_Runtime_Community*
qairt*
QNN*
v2.44.0.260225*.zip
```

Do not use unofficial mirrors, copied device/vendor partitions, or token/cookie based downloads outside Qualcomm's official access flow.

## Recommended Local Placement

Install or extract the SDK beside the existing 2.46 SDK:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

Keep it separate from:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

Do not replace, edit, or overwrite the existing 2.46 SDK or the current overlay.

## Post-Acquisition Checks

After installing QAIRT `2.44.0.260225`, verify at minimum:

```bash
ls -la /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225

find /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  \( -name 'libQnnSystem.so' \
  -o -name 'libQnnHtp.so' \
  -o -name 'libQnnHtpPrepare.so' \
  -o -name 'libQnnHtpV79Stub.so' \)
```

Expected key files:

```text
bin/envsetup.sh
bin/x86_64-linux-clang/qnn-net-run
bin/x86_64-linux-clang/qnn-platform-validator
lib/aarch64-android/libQnnSystem.so
lib/aarch64-android/libQnnHtp.so
lib/aarch64-android/libQnnHtpPrepare.so
lib/aarch64-android/libQnnHtpV79Stub.so
lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so
```

Then record ELF metadata for at least `libQnnSystem.so`:

```bash
file /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225/lib/aarch64-android/libQnnSystem.so
readelf -n /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225/lib/aarch64-android/libQnnSystem.so
sha256sum /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225/lib/aarch64-android/libQnnSystem.so
```

Also record:

- file sizes
- SHA-256
- GNU Build IDs
- `NEEDED`
- version/manifest metadata

The repository now has a sanity check helper:

```bash
bash scripts/check_qairt244_sdk.sh \
  /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

This helper refuses roots that resolve to the known `2.46.0.260424` SDK and records SHA-256, Build IDs, `NEEDED`, and version strings under:

```text
artifacts/qairt244_sdk_check/<timestamp>/
```

If the official SDK is downloaded as a zip or extracted directory, stage it with:

```bash
bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/v2.44.0.260225.zip
```

or:

```bash
bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/qairt/2.44.0.260225
```

The staging helper:

- copies only into `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`
- refuses to overwrite an existing destination
- rejects known 2.46 overlays
- runs `scripts/check_qairt244_sdk.sh` after placement
- does not write into `app/src/**/jniLibs`
- does not build LiteRT
- does not run `Engine.initialize`

## Exact-Match Rebuild and Compare Command

After QAIRT `2.44.0.260225` exists locally:

```bash
bash scripts/run_qairt244_rebuild_compare.sh
```

This wrapper refuses to run if the QAIRT 2.44 path is missing or resolves to the
known 2.46 overlay. If the exact SDK is present, it runs:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

The build helper creates a per-run overlay under the build artifact directory
and does not modify the existing 2.46 overlay.

Expected output shape:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/
artifacts/qairt244_rebuild_compare/<timestamp>/
```

## Safety Rules

- do not copy QAIRT libraries into `app/src/**/jniLibs`
- do not stage rebuilt artifacts into any app flavor
- do not run `Engine.initialize`
- do not run NPU inference
- do not create `Conversation` or `Session`
- do not call `generateResponse`
- do not set `selectedPath=npu`

## Licensing / Redistribution

QAIRT/QNN SDK artifacts may have redistribution restrictions. A local build experiment does not imply the resulting `.so` files can be committed, shared, or shipped. Review Qualcomm license terms before distributing SDK-derived runtime libraries or build outputs.
