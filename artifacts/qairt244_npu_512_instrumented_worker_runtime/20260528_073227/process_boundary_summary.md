# Process boundary results

| prompt_index | slug | boundary | classification | can_dispatch | process_disappeared_suspect | reuse_allowed | hidden_per_run_isolated_required | pidof |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `konnichiwa` | `before_dispatch` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 1 | `konnichiwa` | `after_dispatch` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 1 | `konnichiwa` | `after_result_or_timeout` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 1 | `konnichiwa` | `after_cleanup` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 1 | `konnichiwa` | `after_10s` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 2 | `python_calculator` | `before_dispatch` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 2 | `python_calculator` | `after_dispatch` | `PROCESS_PRESENT` | `true` | `false` | `true` | `false` | `2618` |
| 2 | `python_calculator` | `after_result_or_timeout` | `PROCESS_DISAPPEARED_AFTER_CLEANUP` | `true` | `true` | `false` | `true` | `none` |
| 2 | `python_calculator` | `after_cleanup` | `PROCESS_DISAPPEARED_AFTER_CLEANUP` | `true` | `true` | `false` | `true` | `none` |
| 2 | `python_calculator` | `after_10s` | `PROCESS_DISAPPEARED_AFTER_10S` | `true` | `true` | `false` | `true` | `none` |
