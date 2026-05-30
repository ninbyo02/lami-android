# Memory and LMK review

## Meminfo evidence

Prompt 1:

- after run: pid `4758`, total PSS `309817 KB`
- after 10s: pid `4758`, total PSS `275889 KB`

Prompt 2:

- after run: pid `4758`, total PSS `306544 KB`
- after 10s: `No process found for: io.github.ninbyo02.lami`

Prompt 3:

- after run: `No process found for: io.github.ninbyo02.lami`
- after 10s: `No process found for: io.github.ninbyo02.lami`

## Interpretation

Prompt 2 does not show high retained memory in the normal sense. The process is
gone after 10 seconds, so retained app memory cannot be evaluated for that
interval.

No saved logcat artifact contains an explicit LMK or low-memory kill marker.
However, Android may kill a background or cached process without a useful line
in the captured logcat tail. Because prompt 2 immediately after-run meminfo
still shows one Activity and pid `4758`, the disappearance happens after clean
native cleanup and after the immediate post-run memory capture.

## Classification impact

`os_killed_cached_process_possible` remains plausible but unproven. The safer
classification is `process_disappearance_unexplained`.
