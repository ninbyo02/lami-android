# Process Classification Matrix

| Classification | Meaning | Runtime policy |
| --- | --- | --- |
| `PROCESS_PRESENT` | `pidof` or package `ps` row is present. | Dispatch/reuse may continue if lifecycle gate is also clean. |
| `PROCESS_ABSENT_BEFORE_DISPATCH` | Process is absent before the next prompt is dispatched. | Stop before dispatch. Mark suspect and require per-run isolation. |
| `PROCESS_DISAPPEARED_AFTER_DISPATCH` | Process was absent after broadcast dispatch. | Stop sequence. Mark suspect and require per-run isolation. |
| `PROCESS_DISAPPEARED_AFTER_CLEANUP` | Process was absent after result/cleanup boundary. | Stop sequence. Mark suspect and require per-run isolation. |
| `PROCESS_DISAPPEARED_AFTER_10S` | Process was absent at the post-run 10 second boundary. | Stop sequence. Mark suspect and require per-run isolation. |
| `PROCESS_STATE_UNKNOWN` | Snapshot could not establish process state. | Do not dispatch as clean evidence; review artifact before rerun. |

Any `PROCESS_DISAPPEARED_*` classification is surfaced as
`PROCESS_DISAPPEARED_SUSPECT`.
