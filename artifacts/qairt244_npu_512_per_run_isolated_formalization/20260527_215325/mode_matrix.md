# Mode matrix

| Mode | Max tokens | Execution | Status |
| --- | ---: | --- | --- |
| `hidden_experimental_256` | 256 | hidden experimental | baseline candidate maintained |
| `hidden_per_run_isolated_512` | 512 | force-stop before/after each prompt | hidden candidate formalized |
| sequential 512 | 512 | same process sequential | rollback |
| Activity-restart-only 512 | 512 | lifecycle restart without process isolation | rollback |
| normal ChatScreen 512 | 512 | assistant/UI route | blocked |
| H1 512 | 512 | transient UI surface | blocked |
| 1024/2048/4096 | >512 | any | blocked |

H1 remains pinned to `sanitizer_only + max_output_tokens=128`.
