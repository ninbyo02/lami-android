# PCM decoder NPU feasibility and acceptance plan

## Evidence and judgment

The current speech-decoder-dynamic-et14.pte is lowered with XNNPACK (scripts/voice/export_dynamic_decoder.py), takes int64 codec indices shaped [1,16,frames], and permits 2..256 frames. Its exporter explicitly handles causal convolution padding. There is no successful NPU decoder conversion or device benchmark established by this investigation. NPU feasibility remains a hypothesis, not a supported backend.

PCM decoding is worth an independent NPU feasibility experiment: unlike autoregressive CP calls it processes a batch of frames, potentially amortizing launch/transfer overhead. This is architectural reasoning, not a speed guarantee. Existing slow NPU CP results cannot be extrapolated to this different graph. Variable frame length, convolution/layout conversion, padding, gather/index inputs and any unsupported subgraphs must be audited on the actual exported graph. Do not assert QNN operator compatibility from model names alone.

## Order

1. Fixed captured-code CPU decoder comparison with explicit 1/2/4 threads; fresh per-setting module, warmup, alternating order, PCM hash/equality. Host timing does not predict SM8750.
2. Android separate decoder process comparison on identical codes; record requested/effective runtime settings and distinguish load, forward, output transport. Back up/restore APK and phone artifacts.
3. Export one fixed shape (initially 8 or 16 frames) and obtain conversion/partition report. Identify CPU fallback boundaries and quantify transfers; conversion success alone is not acceleration evidence.
4. Test on SM8750: cold load, repeated warm execution, end-to-end chunk latency, energy/thermal behavior, PCM errors and listening review. Quantization may alter waveform; exact hash equality is not assumed for an NPU candidate.
5. Only after quality passes, investigate decoder state/receptive field and chunk boundaries. Fixed chunks are not automatically equivalent to full decode; arbitrary independent chunking can damage continuity. Repeated cumulative prefix decoding duplicates work.

## Benefit limit

Current warm serial diagnostic: 3.36 s audio, about 5.4–5.5 s codec generation and 3.03 s PCM forward; synthesis about 10.3 s. Hypothetically making PCM three times faster saves about 2 s of this serial request (roughly 20% total). This is arithmetic, not a measured or predicted NPU gain. Even eliminating PCM completely leaves code generation above audio duration. Real-time throughput therefore still requires codec acceleration and controlled pipeline measurements.
