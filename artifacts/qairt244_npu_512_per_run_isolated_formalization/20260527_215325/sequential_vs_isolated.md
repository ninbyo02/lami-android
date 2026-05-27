# Sequential vs isolated

Sequential 512 and Activity-restart-only 512 both reproduced the Python code
prompt timeout after native pre-RunDecode evidence. The completed result,
cleanup evidence, backend evidence, raw output, and sanitized output were not
available for the timed-out code prompt.

Force-stop between prompts passed all three approved prompts:

- `こんにちは`: `natural_japanese`
- `Pythonで簡単な電卓コードを書いて`: `useful_code`, indentation preserved, code fence completed
- `ラミィのNPU推論について短く説明して`: `natural_japanese`

This formalizes force-stop process isolation as a required part of the 512
hidden candidate gate. It does not prove sequential 512.
