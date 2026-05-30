# Prompt 3 state review

Prompt: `ラミィのNPU推論について短く説明して`

## Broadcast

`run_512_lami_npu_short/broadcast.txt` shows:

```text
Broadcasting: Intent { act=io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT flg=0x10400000 cmp=io.github.ninbyo02.lami/.npu.StandardHiddenQairt244PromptReceiver (has extras) }
Broadcast completed: result=0, extras: Bundle[{callingUid=2000}]
```

## State/result/native files

All prompt 3 app-private output files are missing:

- receiver state: `cat: files/qairt244_standard_hidden_prompt_state.txt: No such file or directory`
- result: `cat: files/qairt244_short_multitoken_smoke_result.txt: No such file or directory`
- native diag: `cat: files/qairt244_native_diag.txt: No such file or directory`
- cleanup: `cat: files/qairt244_dev_npu_ui_cleanup_state.txt: No such file or directory`

Lifecycle parser result:

- `lifecycle_classification=TIMEOUT_SUSPECT`
- `expected_run_id=unavailable`
- `observed_run_id=unavailable`
- `cleanup_elapsed_ms=missing`
- `engine_close_evidence=false`
- `native_completed_evidence=false`
- `result_completed_evidence=false`
- `stale_result_rejected=false`
- `run_id_mismatch_rejected=false`

## Conclusion

Prompt 3 is not a stale-result or run-id mismatch case. It is an actual timeout
from the runner's perspective: the broadcast command returned, but the expected
state/result/native/cleanup files were never created within the 60 second
window after the prompt 2 process disappearance.
