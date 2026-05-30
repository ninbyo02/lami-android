# Sequential vs isolated diff

| Item | sequential 512 code-aware | per-run isolated 512 | Interpretation |
| --- | --- | --- | --- |
| artifact | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/` | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/` | Same max512 artifact family, different process isolation. |
| prompt order | `こんにちは`, Python code, Lami NPU | same order | Order alone does not explain isolated success. |
| process isolation | no force-stop after successful prompt 1 | force-stop before and after every prompt | Strongest observed difference. |
| code prompt result | timeout, receiver success false | success, `useful_code` | Failure is specific to sequential context. |
| code prompt elapsed | runner elapsed `70000 ms` | runner elapsed `14000 ms` | Sequential did not return within the 60 second state wait plus overhead. |
| code prompt native stage | pre-RunDecode reached only | success line written | Sequential reaches native decode boundary but does not complete. |
| cleanup evidence | missing for code prompt | `cleanup_elapsed_ms=130`, `Engine.close=unique_ptr_cleanup` | Missing cleanup is an outcome of no completion, not proof of cleanup bug by itself. |
| memory/process after run | process remains after final run and recovers after 10 seconds | no process after each post-run force-stop | Isolated mode removes warm-process state before each prompt. |
| side effects | false where completed; timeout prompt unavailable | false for all completed prompts | No DB/TTS/Markdown/streaming ingress evidence. |

Conclusion: the differentiator is not prompt text, max token guard, sanitizer,
or QNN availability. The differentiator is sequential warm-process state versus
fresh-process force-stop bracketing.
