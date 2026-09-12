# Standard GPU OpenCL runtime

The StandardDebug device build uses the combined OpenCL + NPU runtime pinned in `config/standard_gpu_npu_runtime.json`. Every retained NPU/LiteRT component must match before the OpenCL provider is staged. No native binaries are tracked in Git.

Stage the already validated 13-library NPU inputs in `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a`, then run:

```sh
python3 scripts/stage_standard_gpu_opencl.py --native-dir app/src/customBuildExperimentDebug/jniLibs/arm64-v8a --artifact /absolute/path/to/verified-candidate.apk
./gradlew assembleStandardDebug
```

The artifact can also be the pinned `libLiteRtOpenClAccelerator.so`. Its bytes must match the manifest; an arbitrary same-name library is rejected. This reproduces the binary inputs, not a build from upstream source. The reference APK hash records the experimentally validated provenance; library hashes are the acceptance criteria.

Gradle validates the complete input set, excludes competing arm64 ClGl/Gpu providers, preserves native bytes and validates the final APK. No post-build APK modification is needed. The verified runtime selects the existing incremental callback path instead of the forced blocking GPU path. Other runtimes retain their previous routing.

`-Plami.allowMissingQairt244Jni=true` is still a non-NPU CI smoke build, with the new runtime disabled. It must not be installed as the daily NPU app. StandardRelease remains unchanged by default; an explicitly enabled NPU Release candidate also uses the pinned runtime and requires separate device qualification. `-Plami.standardGpuOpenClEnabled=false` permits controlled comparison with the previous configuration.

Runtime changes require updating the manifest only after coherent GPU/NPU qualification. Do not replace individual hashes to make a failing validation pass.
