# LiteRT / LiteRT-LM Custom Build Source Mapping

Date: 2026-05-16

This note maps the source/tag candidates for a future Qualcomm dispatch custom build. No build was started during this phase.

## Current Problem To Solve

`galleryStackExperimentDebug` now aligns the Java/Kotlin API with the Gallery SM8750 JNI descriptor:

- Java API: `litertlm-android:0.11.0`
- Gallery SM8750 native stack:
  - `liblitertlm_jni.so` Build ID `76e4dccd9c5f9cba468d9cae7becfec0`
  - `libLiteRt.so` Build ID `869121bd7f4b0b77fa581218117a5c14`
  - `libLiteRtDispatch_Qualcomm.so` Build ID `643ad77b8ac2f54bd1b61e4133c77b3a`
- `Backend.NPU(String)` instantiate: success
- `EngineConfig.backend = Backend.NPU(...)`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize()`: SIGABRT
- classification: `no-usable-dispatch-runtime` / `dispatch-runtime-compatibility-mismatch`

The next useful build must keep `liblitertlm_jni.so`, `libLiteRt.so`, and `libLiteRtDispatch_Qualcomm.so` in the same source/API generation. A public HEAD dispatch-only build is not a safe first move.

## Source Candidates

| Repo | Candidate | Evidence | Confidence | Notes |
| --- | --- | --- | --- | --- |
| `google-ai-edge/LiteRT-LM` | tag `v0.11.0`, commit `c87189528a758db32ead241f4fc9c64836398ee7` | remote tag exists; Maven `litertlm-android:0.11.0` Java descriptor matches Gallery JNI descriptor; current `galleryStackExperimentDebug` uses this Java API successfully enough to remove the earlier CheckJNI SIGSEGV | medium-high for Java API | Native Build IDs still do not prove that public tag output equals Gallery or Maven native payload. |
| `google-ai-edge/LiteRT-LM` | tag `v0.10.2`, commit `476c0bd49429569b2a4685c4db7a657d531d4b6e` | remote tag exists | low | Not used by current working stack. |
| `google-ai-edge/LiteRT-LM` | tag `v0.10.1`, commit `c7b77b579596966b60333fd393a1ff49026545ba` | remote tag exists | low | Not used by current working stack. |
| `google-ai-edge/LiteRT` | commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` | `LiteRT-LM v0.11.0` `WORKSPACE` sets `LITERT_REF` to this commit | high as public LiteRT baseline for LiteRT-LM `v0.11.0` | Best source candidate for query/build experiments tied to `litertlm-android:0.11.0`. |
| `google-ai-edge/LiteRT` | tags `v2.1.x` | current public tags exist | low for this issue | Public tags/HEAD may contain dispatch API changes that do not match the Maven/Gallery native artifacts. Avoid using HEAD first. |
| `google-ai-edge/gallery` | tag `1.0.12`, commit `302f7e463b19f45f51825f4ec2fd30309366cb06` | matches Gallery APK `versionName 1.0.12`; local source shows `litertlm = "0.10.0"` | high for public Gallery source tag | The SM8750 APK native payload is not explained by public Maven `0.10.0` alone. |
| `google-ai-edge/gallery` | tag `1.0.13`, commit `edbc39fc4f116714fe0f475e8289067ba13e8a11` | remote release tag exists | medium | Useful for diffing release packaging, but current APK is 1.0.12. |

## LiteRT-LM v0.11.0 Workspace Evidence

Local source inspection of `LiteRT-LM v0.11.0` found:

```text
LITERT_REF = "47615eb6eaec25e8dfcd1aba922c560a57cba0a2"
TENSORFLOW_REF = "49e7f1937d1509dd7fea41bff9ccc994baa97258"
```

That makes `google-ai-edge/LiteRT` commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` the first source candidate for any future `litertlm-android:0.11.0`-aligned build investigation.

## Gallery 1.0.12 Evidence

Public Gallery `1.0.12` source uses:

```text
litertlm = "0.10.0"
com.google.ai.edge.litertlm:litertlm-android
```

However, the Gallery SM8750 APK contains:

| Library | Gallery SM8750 Build ID | Public Maven comparison |
| --- | --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | different from local Maven `0.10.0` and `0.11.0` native payloads |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | different from local Maven `0.11.0` |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | not present in Maven AARs |

Conclusion: Gallery source tag `1.0.12` is useful for app-level provenance, but the SM8750 APK appears to include a special native payload that is not reproduced by simply resolving public `litertlm-android:0.10.0`.

## Recommended Source Baseline

For the next phase, start with source-only query work from:

1. `LiteRT-LM v0.11.0` (`c87189528a758db32ead241f4fc9c64836398ee7`)
2. its pinned LiteRT commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`
3. Gallery `1.0.12` (`302f7e463b19f45f51825f4ec2fd30309366cb06`) as packaging/reference material

Do not build public HEAD dispatch first. If a build is eventually attempted, it should use the pinned LiteRT commit from LiteRT-LM `v0.11.0` unless maintainers provide a more exact source/build provenance.

