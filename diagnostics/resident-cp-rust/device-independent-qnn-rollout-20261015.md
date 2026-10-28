# Independent QAIRT qnn-net-run 16-step carried-KV parity (2026-10-15 06:35 JST)

Used the same production-fp32 context, fixed embedding SHA256
17cb58bdf8e35e694e07b98e2289ccaead252188a165c6ce0d085dd5629f04d5,
position 0..15, zero initial K/V, mask 0 up to active position and -10000 thereafter.
The reference program is the Qualcomm QAIRT 2.47 qnn-net-run, with every K/V
output fed into the next inference's input via separate step input files.

All 16 reference steps returned successfully. The final hidden output at
position 15 had SHA256:
2eced53f36b33b91b0f447f1b3020dbb6fa6d5c1526f66ddb68c09b02fd96e44.

That equals the independent Rust JNI single-context 16-step run final hidden
output SHA256 on SM8750, measured at 06:28 JST.

Limitations: only the final hidden tensor was compared with the Rust path;
all intermediate hidden tensors and K/V outputs from Rust were not exported
or compared byte for byte. The strict Rust cache write-boundary checks did
not fail. CPU-vs-NPU precision, actual speech quality and model end-to-end
latency remain to be verified. Note that the SDK command reloads its AOT context
per step and is **not** an apples-to-apples timing benchmark against resident Rust.
