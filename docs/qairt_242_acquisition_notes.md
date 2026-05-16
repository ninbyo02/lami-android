# QAIRT 2.42.0.251225 Acquisition Notes

Date: 2026-05-17

## Current Status

QAIRT `2.42.0.251225` was not found as a real local SDK install.

Search artifact:

```text
artifacts/qairt242_acquisition/20260517_083526/local_search.txt
```

The local tree currently has QAIRT `2.46.0.260424`, but that is not a substitute
for a QAIRT `2.42.0.251225` comparison root.

No LiteRT build was run, no app was installed, no native library was changed,
and no `Engine.initialize` or NPU inference was executed.

## Why 2.42 Matters

Radxa public Qualcomm NPU documentation for Dragon Q6A uses:

```text
QAIRT 2.42.0.251225
```

That generation is useful as a public ecosystem comparison point because it is
documented for Linux SBC workflows. It is not the current primary SM8750/V79
candidate for Lami:

- Radxa examples target QCS6490/V68 and QCS9075/V73 style flows.
- LiteRT public Qualcomm metadata currently references QAIRT `2.44.0.260225`.
- Lami's local SDK is QAIRT `2.46.0.260424`.
- The target model is a Qualcomm SM8750 `.litertlm` payload.

Therefore QAIRT 2.42 should be treated as a comparison/downgrade experiment, not
as proof of the expected SM8750 Android `Backend.NPU` runtime generation.

## Official Acquisition Path

Use only official Qualcomm/Radxa-documented distribution paths. Do not use
unofficial mirrors, copied vendor partitions, or token/cookie based downloads.

Radxa's documented pattern is:

```bash
export QAIRT_VERSION=2.42.0.251225
wget https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/${QAIRT_VERSION}/v${QAIRT_VERSION}.zip
unzip v${QAIRT_VERSION}.zip
cd qairt/${QAIRT_VERSION}
source bin/envsetup.sh
```

Access may require:

- Qualcomm ID / developer account
- Qualcomm Software Center access
- acceptance of Qualcomm SDK license terms
- Qualcomm Package Manager / QPM in some environments

## Recommended Local Placement

Install or extract the SDK beside the existing QAIRT roots:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225
```

Keep it separate from:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

Do not replace, edit, or symlink it to the existing 2.46 SDK.

## Post-Acquisition Checks

After obtaining QAIRT `2.42.0.251225`, verify it with:

```bash
bash scripts/check_qairt242_sdk.sh \
  /home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225
```

The checker records:

- required file presence
- optional V68/V73/V79 skel/stub presence
- SHA-256
- GNU Build IDs
- `SONAME` / `NEEDED`
- version-related strings

Output:

```text
artifacts/qairt242_sdk_check/<timestamp>/
```

Expected key files:

```text
bin/envsetup.sh
bin/x86_64-linux-clang/qnn-net-run
bin/x86_64-linux-clang/qnn-platform-validator
lib/aarch64-android/libQnnSystem.so
lib/aarch64-android/libQnnHtp.so
lib/aarch64-android/libQnnHtpPrepare.so
```

Optional comparison files:

```text
lib/aarch64-android/libQnnHtpV68Stub.so
lib/hexagon-v68/unsigned/libQnnHtpV68Skel.so
lib/aarch64-android/libQnnHtpV73Stub.so
lib/hexagon-v73/unsigned/libQnnHtpV73Skel.so
lib/aarch64-android/libQnnHtpV79Stub.so
lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so
```

If the official SDK is downloaded as a zip or extracted directory, stage it with:

```bash
bash scripts/stage_qairt242_sdk_from_download.sh ~/Downloads/v2.42.0.251225.zip
```

or:

```bash
bash scripts/stage_qairt242_sdk_from_download.sh ~/Downloads/qairt/2.42.0.251225
```

The staging helper:

- copies only into `/home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225`
- refuses to overwrite an existing destination
- rejects known 2.46 overlays
- runs `scripts/check_qairt242_sdk.sh` after placement
- does not write into `app/src/**/jniLibs`
- does not build LiteRT
- does not run `Engine.initialize`

## Comparison Command

After QAIRT `2.42.0.251225` exists locally:

```bash
bash scripts/compare_qairt_generations.sh \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.42.0.251225 \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

The comparison is static-only. It does not build, install, run the app, or
execute `Engine.initialize`.

## Safety Rules

- do not copy QAIRT libraries into `app/src/**/jniLibs`
- do not stage rebuilt artifacts into any app flavor
- do not run `Engine.initialize`
- do not run NPU inference
- do not create `Conversation` or `Session`
- do not call `generateResponse`
- do not set `selectedPath=npu`

## Licensing / Redistribution

QAIRT/QNN SDK artifacts may have redistribution restrictions. A local comparison
does not imply the resulting `.so` files can be committed, shared, or shipped.
Review Qualcomm license terms before distributing SDK-derived runtime libraries
or build outputs.
