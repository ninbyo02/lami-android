This DEV-only source set is for the `standardGpuMinimalRuntimeCandidateDebug`
runtime alignment candidate.

Stage only these local native libraries here when explicitly testing the
candidate flavor:

- `libLiteRt.so`
- `liblitertlm_jni.so`

Do not commit native `.so` files from this directory. They are licensed native
artifacts and are ignored by `.gitignore`. Use
`scripts/stage_standard_gpu_minimal_runtime_candidate_libs.sh` to stage the
files and write a local manifest under `artifacts/`.
