# Stale Tombstone Note

The diagnostics collector selected `/data/tombstones/tombstone_22`, but that
tombstone is from an older initialize dry-run and does not match this
single-token smoke execution.

- smoke run id: `1779481978822`
- selected tombstone stage: `1779478486470 runId=1779478487993_2 Engine.initialize invoking method=Engine.initialize(): void`
- smoke result file: `result=success`
- timeout state: `timeout=false`
- process alive after smoke: `18212`

Classification for this smoke: no new crash evidence. The selected tombstone is
treated as stale and is not used as the smoke outcome.
