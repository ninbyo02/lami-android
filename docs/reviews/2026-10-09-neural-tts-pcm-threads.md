# Fixed-code PCM decoder host thread results

CPU x86 host only; no SM8750 improvement claim. Two captured cases, three alternating trials, one warmup per case and three measured calls per trial/thread/case. Global ExecuTorch pool is reset before loading each isolated model; no concurrent modules. Output extraction and clone are inside measured intervals.

| Threads | Case 0 median ms | Case 1 median ms |
|---|---:|---:|
| 1 | 1420.46 | 1455.79 |
| 2 | 902.37 | 925.04 |
| 4 | 653.41 | 666.88 |

All 54 measured outputs bit-equal to the first host output: True. This is within-host parity, not parity with Android PCM. Model and input hashes are in the JSON evidence. Timing is scheduling/cache-sensitive; host results justify an Android comparison, not adopting four threads by default.
