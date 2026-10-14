# SM8750 resident CP one-step diagnostic, 2026-10-15 06:06 JST

The isolated io.github.ninbyo02.lami.cpdiagnostic app executed Rust JNI,
QAIRT 2.47 V79 HTP, verified 157749248-byte AOT context and pinned files.
Phone was accessed before the agreed weekday 08:00 cutoff. No normal LAMI APK
or audio was changed.

Run 1: status=STEP_RETURNED, context_load_ns=360972656,
one_step_ns=13167136, golden_bit_equal=false, audio_played=false.

Run 2 with quantitative diagnostics:
status=STEP_RETURNED; golden_bit_equal=false; changed_elements=1024;
max_abs_error=108.4765625; mean_abs_error=2.9085561654064804;
rmse=4.998338726391784; reference_normalized_max_error=1.0061594202898552;
reference_max_magnitude=107.8125; output_sha256=
20774fc7cf04e372092171c6c035b63a15eb4786d04f417c4f99c8743b70ba7f.
context_load_ns=376807448; kv_reset_ns=1557969; one_step_ns=17970885.
native_stats=1,1,24,1,0,138153906,12639062.

Input SHA256: 17cb58bdf8e35e694e07b98e2289ccaead252188a165c6ce0d085dd5629f04d5.
Golden SHA256: 58420cd84987349523d1b90724c82c55a4aa29715d43cd32b4ac16339e8de2a6.
These files were located in inputs/serving_default_embeddings.raw and
trace/position0/Result_0/serving_default_hidden_output.raw, respectively.

IMPORTANT: Large error does not meet accuracy requirements. It is not proof
that the reference output belongs to the same AOT context, embedding, mask,
position or cache state. Verify fixture provenance and data contract before
tuning native inference or claiming output correctness. Two timings are not
a performance benchmark.


## Provenance investigation at 06:11 JST

The first golden raw tensor came from trace/position0 generated with a
**different** trace AOT context (SHA256 3740e377...); the device used
production-fp32 AOT SHA256 1ebaf036....

A second diagnostic on the production-fp32 AOT used hashes:
- Embedding af4986f4525dc536ec3feb552afc2eaad1947b99657c8ac7d69b86b80725bd18
- Reference output 957864d1aef2610fc9c1786f784012f596133d8765d0fce0c0aedf10dafd3ce6.
It returned STEP_RETURNED (one_step_ns=26258958), changed_elements=1024,
max_abs_error=106.15625, rmse=4.926754410502367.

**However**, production-fp32/actual-check/inputs/serving_default_input_ids.raw
contains integer 15, and its KV cache K0 begins with nonzero FP32 data. The
Rust one-step probe sets position=0, initial zero KV caches, and an all-causal
position-0 mask. The original actual-check files therefore are NOT verified
position-zero/zero-cache reference inputs, even though the embedding/output
hashes are valid. Neither quantitative comparison is a valid apples-to-apples
CPU/NPU parity test. The large errors do not establish defective NPU math.

Next generate a provenance-pinned independent **position=0, zero-KV** reference
against the identical production-fp32 context and mask, OR extend the debug
probe to supply all 13 identical reference inputs. Do not claim accuracy or
promote to cute TTS until the inputs and outputs are paired and validated.
