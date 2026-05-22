# Large Artifacts Are Local-Only

This smoke artifact can contain APK-extracted or rebuilt native libraries under
`diagnostics/apk_libs`, `built_libs`, `qnn_runtime_libs`, or `reference_libs`.
Those binaries are intentionally excluded from Git tracking. Preserve only text
metadata such as `summary.md`, `result.txt`, `native_diag.txt`, Build IDs,
hashes, and diff patches in commits.
