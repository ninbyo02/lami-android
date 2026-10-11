# ASR Runtime

`sherpa-onnx-1.13.6.aar` is the pinned Android ASR runtime used by the assistant
integration. Models are installed separately and are not bundled in this archive.

- Upstream: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6
- SHA-256: `0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698`
- Archive includes Sherpa JNI and ONNX Runtime native libraries for Android ABIs.
- Sherpa license: `sherpa-onnx-LICENSE` (Apache-2.0, upstream v1.13.6).
- ONNX Runtime license: `onnxruntime-LICENSE` (MIT; this license copy does not
  establish the native runtime version embedded in the AAR).

The archive is committed to keep clean-checkout builds reproducible. It does not
contain conversation recordings, transcripts, credentials, or ASR model weights.
The license texts are also included in `src/main/assets/licenses` so APK/AAB
distributions retain the corresponding notices.
