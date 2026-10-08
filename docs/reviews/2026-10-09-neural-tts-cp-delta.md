# CP delta output cache isolated pilot

Optional --delta-outputs in the CP exporter returns the five current K/V
positions instead of the full cache. Internal index_copy and full attention
remain unchanged. Default export and Android behavior remain unchanged.
All 35 INT8 weights remain quantized. Candidate SHA256:
0f801eac3fbdd26db5e972d95b79e57362e75582ff09efe24a249da62ee046cf.

Fixed captured inputs at positions 0 and 15 passed bit-exact hidden state and
reconstructed full K/V comparison against grouped INT8 cache16. KV output
shrinks from 655,360 to 40,960 bytes per step. Host median milliseconds:
position0 2.9182 baseline / 2.9291 candidate; position15 2.9300 / 2.8391.
Timing excludes caller reconstruction, with uncontrolled scheduling. No
substantial compute gain is established. Host input tests do not establish
rolling feedback, audio quality or Android speed. Keep pilot isolated until
full feedback and device copying costs have been measured.

The actual cached Android AAR 1.4.0 has Module.load(String,int,int). AAR SHA256:
a4a836b9fadd5b9afdf07b8533b2c3326695d04a58dd37e2cdfe709e804854fb.
Native shared-pool configuration is unresolved for this packaged binary;
no per-CP thread override is introduced.

## Full feedback gate

Two host sentences reached EOS at 49/50 frames. Codes NPY and PCM WAV files
were byte-identical to the grouped main cache128 feedback reference. See
2026-10-09-neural-tts-cp-delta-feedback.json. No duplicate audio is published.
Android debug support pins the delta model hash, checks output shapes and
copies each layer/head delta into owned full cache buffers with absolute
indexing. It does not alias ExecuTorch outputs. Normal full-cache copying is
unchanged. Local CPU smoke build is running; a guarded device comparison
starts only after build success and restores original APK/config/probes/WAV
in finally. Device results and latest CI are still pending.

## Device result: reject Kotlin delta scatter for promotion

All eight serial requests completed with code/PCM hashes matching reviewed
clips. Original APK, manifest, WAV and two probes were restored and hash
verified; temporary models removed. No duplicate Voice Lab audio is needed.
Same CPU smoke APK and grouped main/packed heads throughout, full/delta/delta/full.
Full-cache frame p50: 141,150,123,130 ms; delta: 147,157,157,174 ms.
Weighted CP output-copy means: full 0.1601 ms/call, delta 1.5867 ms/call.
Weighted forward means: full 3.6518 ms, delta 3.6594 ms. This implementation
has materially higher output-update cost and must not be promoted for speed.
Frequency/cache/thermals were uncontrolled; realtime remains unmet.

Likely cause: absolute FloatBuffer get/put for every delta element in Kotlin,
instead of native bulk copy. This attribution needs a native-copy control to
isolate. Next candidate: JNI scatter of contiguous 128-float blocks into owned
cache storage, preserving validation and alias protections. Do not expand the
model pilot until that copy path has been measured. Keep PR draft.

## Native block scatter follow-up (pending)

Replaces per-element Kotlin absolute get/put with JNI memcpy of each contiguous
128-float layer/head block. Direct-buffer dimensions, position and disjoint
address ranges are validated. Startup diagnostic checks copied and untouched
slots at positions 0 and 15, and rejects an invalid position. Runtime model
and sampling remain identical. CPU smoke build and fresh guarded device
comparison are pending; no native scatter speed claim yet.

Budget: the observed Kotlin regression is (1.5867 - 0.1601)*16 = 22.83 ms/frame.
Recovering that regression does not imply beating the original full-cache
path. Its total output-copy budget is only 0.1601*16 = 2.56 ms/frame, which
bounds improvement from eliminating that measured copy alone. The isolated
native scatter experiment therefore targets roughly 0–3 ms/frame improvement
against the old full-cache path; it cannot by itself establish realtime.
