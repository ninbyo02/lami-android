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

Do not substitute arbitrary QNN libraries from another device or APK as an "exact" SDK.
The existing 2.46 symlink overlay is not acceptable for an exact-match QAIRT
2.44 rebuild.

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
