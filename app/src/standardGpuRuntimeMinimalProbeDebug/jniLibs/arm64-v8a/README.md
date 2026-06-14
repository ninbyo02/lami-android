This DEV-only source set is intentionally marker-only.

Do not commit native `.so` files here. The standardGpuRuntimeMinimalProbeDebug
flavor is used to verify whether the dependency-provided LiteRT/LiteRT-LM core
pair (`libLiteRt.so` and `liblitertlm_jni.so`) is sufficient for GPU callback
streaming success without staging Qualcomm dispatch/compiler/provider overlays.
