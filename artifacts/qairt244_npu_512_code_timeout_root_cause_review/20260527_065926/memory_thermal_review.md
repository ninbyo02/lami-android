# Memory and Thermal Review

No direct thermal throttling marker was found in the reviewed log scan. Thermal
or resource slowdown is therefore only a hypothesis.

Memory observations:

- sequential run before: no process
- after prompt 1 `こんにちは`: `TOTAL PSS=300966 KB`,
  `Native Heap=84560 KB`
- after Python timeout: no process, because the runner force-stopped the app
- after prompt 3: `TOTAL PSS=299653 KB`, `Native Heap=82556 KB`
- final after: `TOTAL PSS=296849 KB`, `Native Heap=82672 KB`
- after 10 seconds: `TOTAL PSS=275865 KB`, `Native Heap=53864 KB`

Isolated 512 code retry:

- before: no process
- after: `TOTAL PSS=251268 KB`, `Native Heap=33172 KB`
- after 10 seconds: `TOTAL PSS=258999 KB`, `Native Heap=33172 KB`

Interpretation:

- There is no retained-memory rollback after the sequential artifact; memory
  decreased after 10 seconds.
- The sequential code prompt starts after a warm prior NPU run with higher
  native/process memory than the isolated code-only success.
- This supports testing per-run process freshness as a future isolation axis,
  but it does not prove memory high-retention as the cause.
