# Main cache128 Android diagnostic pilot

The debug-only prepared voice synthesizer selects 128 KV slots only for `main-int8-permute-cache128.pte` with SHA256 `ec6e5027e623b054f711905113c1424b0a6de0cc7e43dc11b24ccc03b0dc1d8c`. Other main programs retain 256 slots. The original manifest capacity remains 256 because its frontend/RoPE bundle is unchanged.

The selected capacity controls decoder allocation and the generation bound. Prefill that leaves no generation capacity fails before module loading. Existing EOS enforcement withholds incomplete final audio. Main program and capacity are recorded in diagnostic metrics.

The isolated device comparison fixes CP INT8 cache16 and alternates main capacities 256/128/128/256, two held-out Japanese sentences per fresh process. Current APK, manifest, WAV and probe files are backed up before mutation and restored with hash checks in a finally block. The APK is a voice-only diagnostic artifact built with the explicit missing-QAIRT-JNI allowance, not NPU promotion evidence.

Host evidence and reduced long-input capacity limits: see `2026-10-07-neural-tts-main-cache128.md`. Device results remain pending and must not be inferred from the host speed ratio.

Artifacts: `/home/sato/project/lami-android-voice-dataset/device/main-cache128-20261007/`.

Validation: `:app:assembleStandardDebug -Plami.allowMissingQairt244Jni=true` passed on JDK21, and `git diff --check` passed. Device comparison started after a fresh backup; results and restoration verification remain pending.
