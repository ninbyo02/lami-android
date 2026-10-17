# SM8750 Rust JNI CP vs independent qnn-net-run (2026-10-15 06:21 JST)

- Context: production-fp32/aot/mtp-qnn-context.bin SHA-256 1ebaf036a376c886fe135afbf5f87dfb651be2804dc8675f1035be9ca7a92136
- SDK: QAIRT 2.47, HTP V79 / SM8750.
- Generated 13-input fixture with position=0, 1024-float embedding hash af4986f4525dc536ec3feb552afc2eaad1947b99657c8ac7d69b86b80725bd18, mask [0,-10000 x31], ten zero-filled KV tensors.
- SDK official qnn-net-run: 1 invocation, context restored and graph executed; result serving_default_hidden_output.raw was 4096 bytes. SHA-256: 4328738d582c28f8ebe2313066cc0fd21867b4482248e0493b64467a0686ac16.
- Earlier on-device Rust JNI one-step with the same embedding/context/position/zero-cache reported output_sha256 4328738d582c28f8ebe2313066cc0fd21867b4482248e0493b64467a0686ac16.
- Therefore the Rust JNI and independent QAIRT run produced byte-identical 1024-float hidden output for this one synthetic case.
- **This is QNN-vs-QNN parity, not CPU-vs-NPU precision validation.** No 16-step cache rollout or speech output was tested in this comparison.
- Normal LAMI application, existing cute voice and other jobs untouched. Device-only diagnostic paths used.
