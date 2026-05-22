# QAIRT 2.44 libcdsprpc Staging Result

Date: 2026-05-22

The staging experiment was prepared but not executed.

`customBuildExperimentDebug` gained a fallback script:

```text
scripts/stage_device_libcdsprpc_for_custom_experiment.sh
```

The script is intentionally scoped to `customBuildExperimentDebug`:

- pulls a device-provided `libcdsprpc.so` into `artifacts/`
- stages it only into
  `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/libcdsprpc.so`
- records SHA-256, Build ID, `DT_NEEDED`, `file`, and `readelf` outputs
- records `git check-ignore` proof
- relies on the source-set local ignore rule:
  `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/.gitignore`

This fallback is not the preferred path because the manifest-only experiment
resolved vendor visibility without packaging or redistributing the vendor
library.

No device vendor library was committed.
