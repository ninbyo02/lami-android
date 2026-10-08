# CP thread count fixed-input host probe

Verified grouped INT8 CP cache16 model, saved actual host position15 input,
16 traced executions per process, three alternating trials at 1, 2 and 4 CPU
threads. Median Method::execute milliseconds: {"1": 2.0740888125000003, "2": 1.9842408125, "4": 2.4522135625}.

This short host x86 probe does not establish Android speed or output equality.
Scheduling and cache state are uncontrolled; tracing overhead is included.
Four threads did not improve this sample. Most time remains in XNNPACK
(delegate calls), with roughly 0.3–0.4 ms in native operations. Changing the
Android thread count without measuring exact outputs and end-to-end timing
is not justified. Next: inspect Android runtime thread configuration and
prepare a fixed-input device probe with thread-local/runtime controls only
if the API supports it. Keep app defaults unchanged.

The reusable script pins the model hash and records input hashes. It refuses
an existing output directory. No model or Android code is modified.
