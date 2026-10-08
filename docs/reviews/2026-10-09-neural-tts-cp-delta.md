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
