# Isolated QAIRT 2.47 voice trial build

The source APK can now be assembled with `-Plami.voiceIntegrationTrial=true`
without staging the QAIRT 2.44 custom runtime. Previously that flag changed the
package name but still required `stageQairt244StandardDebugNativeLibs` and both
2.44 overlay tasks, so a clean checkout failed before producing an APK.

For the explicit trial only, Gradle disconnects the generated 2.44 JNI source
directory and overlay tasks, disables the combined 2.44 OpenCL runtime flag, and
excludes old QNN libraries supplied by dependencies. It retains the normal app
sources, resources, LiteRT-LM GPU implementation, and ExecuTorch implementation.
The source APK is an intermediate artifact; the existing verified packager adds
the pinned QAIRT 2.47 libraries and newly built resident CP JNI. It rejects an
incorrect application ID, legacy/mixed QNN payloads, mismatched model files,
missing JNI provenance, or altered app payloads.

The normal build still uses the existing 2.44 source directory, staging,
overlays, and OpenCL checks. The trial flag cannot be combined with the other
diagnostic identities or `lami.standardNpuRuntimeEnabled=true`.

## Reproduce with installed local inputs

Use JDK 21 and the existing Android SDK/NDK. Set `SDK247` to the verified QAIRT
2.47.0.260601 SDK and `MODEL`/`MODEL_SHA256` to the independently pinned
`lami_cute` model manifest. The model and SDK are read-only inputs.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/sato/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew --offline --no-daemon :app:assembleStandardDebug \
  -Plami.voiceIntegrationTrial=true

./gradlew --offline --no-daemon :app:testStandardDebugUnitTest \
  -Plami.voiceIntegrationTrial=true \
  --tests 'io.github.ninbyo02.lami.tts.*' \
  --tests 'io.github.ninbyo02.lami.ui.screens.home.ResponseSpeechSessionTest'

python3 scripts/voice/build_resident_cp_android_jni.py \
  --sdk "$SDK247" --ndk "$ANDROID_HOME/ndk/28.2.13676358"

mkdir -p build/voice-trial-delivery
python3 scripts/voice/package_voice_integration_trial.py \
  --source app/build/outputs/apk/standard/debug/app-standard-debug.apk \
  --sdk "$SDK247" \
  --jni diagnostics/resident-cp-rust/target/lami-arm64/liblami_resident_cp_diag.so \
  --model-manifest "$MODEL" --model-manifest-sha256 "$MODEL_SHA256" \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" \
  --keystore /home/sato/.android/debug.keystore \
  --output build/voice-trial-delivery/lami-voice-trial-qairt247.apk \
  --result build/voice-trial-delivery/result.json
```

Delivery paths must be new: the packager refuses to overwrite an APK or result.
The packager additionally runs the maintained Android-free host speech tests,
signs the provenance asset, verifies the final signature and library hashes,
and checks that the assembled app payload was preserved.

## Acceptance boundary

Host assembly, signature verification, JNI provenance, model hashes, and JVM
tests are host evidence. They do not establish Android audio playback, ordinary
LLM streaming on a phone, NPU execution, device speed, EOS/Stop on hardware, or
runtime fallback. The machine-readable packaging receipt keeps those fields
unverified. No device operation is part of these commands.
