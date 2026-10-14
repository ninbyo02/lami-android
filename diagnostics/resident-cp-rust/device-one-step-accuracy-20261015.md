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
