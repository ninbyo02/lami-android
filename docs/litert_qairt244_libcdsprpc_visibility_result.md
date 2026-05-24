# QAIRT 2.44 libcdsprpc Visibility Result

Date: 2026-05-22

Scope: `customBuildExperimentDebug` only. No normal UI NPU path was changed.
No `Conversation`, `Session`, `generateResponse`, or token smoke was run.

## Change

`customBuildExperimentDebug` now uses its own manifest:

```text
app/src/customBuildExperimentDebug/AndroidManifest.xml
```

That manifest declares:

```xml
<uses-native-library android:name="libcdsprpc.so" android:required="false" />
```

The shared `npuExperimentDebug` manifest was not edited, so the manifest change
does not apply to `npuExperimentDebug`, `galleryStackExperimentDebug`,
`standard`, or release builds.

## Artifact

```text
artifacts/qairt244_libcdsprpc_manifest_experiment/20260522_231302/
```

Key files:

- `apk_manifest_xmltree.txt`
- `dumpsys_package.txt`
- `apk_zip_listing.txt`
- `qairt244_native_diag.txt`
- `stage_file.txt`
- `probe_snapshot.txt`

## Package Result

APK manifest dump:

```text
E: uses-native-library
  A: android:name="libcdsprpc.so"
  A: android:required=(type 0x12)0x0
```

Installed package dump:

```text
usesOptionalNativeLibraries:
  libcdsprpc.so
  libvndksupport.so
  libOpenCL.so
usesLibraryFiles:
  libcdsprpc.so
  libOpenCL.so
```

`apk_zip_listing.txt` contains no packaged `libcdsprpc.so`, so this was a
vendor visibility experiment, not local redistribution or staging of the vendor
library.

## Dry-Run Result

The explicit initialize-only dry-run returned successfully:

```text
Engine.initialize returned resultClass=com.google.ai.edge.litertlm.Engine
initialize result=success
close result=success
```

The previous `libcdsprpc.so not found in namespace clns-9` failure disappeared.

QNN backend trace:

```text
QnnDevice_create started
Attempting to open dynamically linked so:
  .../lib/arm64/libQnnHtpV79Stub.so using absolute filename
First connection to QNN stub established!
QnnDevice_create done. device = 0x1. status 0x0
```

LiteRT dispatch trace:

```text
dispatch vendor initialize status=kLiteRtStatusOk(0)
InitializeDispatchApi LiteRtDispatchInitialize success
InitializeDispatchApi LiteRtDispatchCheckRuntimeCompatibility status=kLiteRtStatusOk(0)
DispatchDelegate::Initialize InitializeDispatchApi success has_dispatch_runtime=true
```

## Classification

`uses-native-library` resolves the FastRPC host dependency visibility issue for
this device and build:

```text
libQnnHtpV79Stub.so -> libcdsprpc.so -> vendor native library visible
```

The current QAIRT 2.44 custom stack can complete `Engine.initialize` in the
explicit dry-run path. This is still not an inference proof; generation remains
intentionally untested.

## Staging Experiment

The local staging path is documented and scripted as a fallback:

```text
scripts/stage_device_libcdsprpc_for_custom_experiment.sh
```

It was not needed and was not run for this result. Device vendor
`libcdsprpc.so` was not committed or redistributed.
