# LiteRT / LiteRT-LM Custom Build Environment Readiness

Date: 2026-05-16

This is a readiness snapshot for future source query/build work. No custom build was run.

## Host

| Item | Value | Status |
| --- | --- | --- |
| OS | Ubuntu 24.04 family, Linux `6.14.0-34-generic`, `x86_64` | usable |
| Java | OpenJDK `17.0.18` | usable |
| Python | Python `3.12.3` | usable |
| CMake | `3.28.3` | usable |
| Ninja | `1.11.1` | usable |
| ADB | Android Debug Bridge `1.0.41`, version `34.0.4-debian` | usable |
| Disk | `/home` has about 2.3T available; `/` has about 796G available | usable |

## Android SDK / NDK

| Item | Observed value | Status |
| --- | --- | --- |
| `ANDROID_HOME` | `/home/sato/Android/Sdk` for Bazel query/cquery script | set locally by script |
| Additional SDK path | `/home/sato/Android/Sdk` | present |
| Android platforms | `android-35`, `android-36` under `/home/sato/Android/Sdk/platforms` | usable for app work |
| Android command-line tools | `/home/sato/Android/Sdk/cmdline-tools/latest`, `sdkmanager 20.0` | installed |
| NDK | `/home/sato/Android/Sdk/ndk/28.2.13676358` / r28c | installed for Bazel Android query/cquery |
| NDK clang | Android clang `19.0.1` | present |

## Bazel

| Tool | Status |
| --- | --- |
| `bazel` | `/home/sato/.local/bin/bazel` symlink to Bazelisk |
| `bazelisk` | `/home/sato/.local/bin/bazelisk`, version `v1.29.0` |
| Bazel selected by LiteRT-LM `.bazelversion` | `7.6.1` |

Bazelisk is now sufficient for query/cquery. Build remains a separate approval phase.

## QAIRT / QNN

| Item | Observed value | Status |
| --- | --- | --- |
| Local QAIRT-like directory | `/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424` | present |
| `QAIRT_HOME` / QNN env vars | not set in current shell | needs setup |
| QNN tools in checked path | `qnn-net-run` and `qnn-platform-validator` found under QAIRT `2.46.0.260424` for several host/target triples, including `x86_64-linux-clang` and `aarch64-android` | usable after env/path setup |
| Android QNN libs | `lib/aarch64-android/libQnnSystem.so` found under QAIRT `2.46.0.260424` | candidate input, license/redistribution still needs review |
| Device-side prior validation | External QAIRT/QNN GPU/DSP/HTP passed on NX733J / SM8750 | hardware capability exists, but build env still needs local SDK setup |

Do not assume the local QAIRT path is sufficient until headers, Android libs, and license/redistribution constraints are verified.

## Existing Local Inputs

| Path | Contents | Notes |
| --- | --- | --- |
| `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk` | Gallery SM8750 APK | used for native stack analysis |
| `/tmp/google-ai-edge-gallery-1.0.12` | Gallery source checkout/tag `1.0.12` | source reference only |
| `/tmp/litert-lm-v0.11.0` | LiteRT-LM `v0.11.0` source checkout | source reference only |
| `/tmp/litert-47615` | LiteRT commit `47615eb6...` checkout | source reference only |
| Gradle cache | `litertlm-android:0.10.0`, `0.11.0` | Maven artifact comparison inputs |

## Readiness Summary

Ready for query/cquery:

- Java, Python, CMake, Ninja, ADB.
- Bazelisk / Bazel `7.6.1`.
- Android NDK r28c.
- Disk space.
- Source/tag mapping is sufficient for source-only inspection.
- Gallery APK and local Maven artifacts are available for static comparison.

Blocked before build:

- Confirm QAIRT/QNN SDK version compatibility. LiteRT source expects QAIRT `2.44.0.260225`; local SDK is `2.46.0.260424`.
- Confirm QNN SDK headers and Android libraries are legally usable for this build experiment.
- Confirm source tag/commit target with maintainers if possible before building anything intended for app testing.
