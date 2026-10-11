# PCM decoder device thread results

NX733J/SM8750, identical codes per sentence. Three alternating trials and two measured calls per setting; model-load warmups excluded. Forward timing includes output extraction.

| Requested threads | Sentence 0 median ms | Sentence 1 median ms |
|---|---:|---:|
| 1 | 11556.0 | 8563.5 |
| 2 | 5852.5 | 4426.0 |
| 4 | 3186.5 | 2373.0 |

All 36 measured PCM outputs bit-equal to the default reference; accepted code/PCM hashes match both sentences. Original APK, manifest, WAV and two probes restored and hash verified. Requested thread counts do not establish core placement. Frequency/thermal/cache state uncontrolled. No default promotion and no NPU claim.

Fixed-8 portable and fixed-16 XNNPACK CPU exports also passed the host max-absolute-error threshold 0.002 against dynamic XNNPACK prefixes. Prefix-only equivalence does not establish independent chunk continuity. The pre-lowering operator inventory is not a QNN support/partition report.
