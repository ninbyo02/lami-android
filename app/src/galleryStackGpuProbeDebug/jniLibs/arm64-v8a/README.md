# galleryStackGpuProbe native libs

This directory is reserved for the DEV-only `galleryStackGpuProbeDebug` flavor.

Do not commit native `.so` files here. Stage local test libraries only with:

```bash
scripts/stage_gallery_stack_gpu_probe_native_libs.sh --stage
```

The standard flavor and `standardDebug` native library inputs must not use this directory.
