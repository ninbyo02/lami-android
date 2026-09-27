# Server device review — 2026-09-11

Baseline: main c43710f1. Device: NX733J, Android 16. Standard Debug rebuilt from main and installed with data preserved. Initial server QA completed around 05:40 JST.

## Device observations

| Case | Result | Evidence |
| --- | --- | --- |
| Lemonade Qwen3.8 normal response | Pass | One completed assistant row; content TTFT 232 ms |
| Lemonade content cancellation / resend | Pass | Partial row CANCELLED; subsequent marker response COMPLETED |
| Lemonade Python sieve | Pass | 531-character fenced reply; Python AST parsed; indentation 0/4/8/12/16 spaces |
| Ollama Qwen3.8 thinking | Pass | 147 thinking characters / 52 chunks; thinking TTFT 7,379 ms; answer TTFT 16,798 ms; saved answer `323` |
| Ollama thinking cancellation / resend | Pass | Empty cancelled assistant row, no reasoning content saved; next reply completed |
| Ollama Gemma3n non-thinking | Pass for normal response | Zero thinking metrics; normal marker reply completed |
| Ollama Gemma3n code | Model/server issue | U+2581 indentation markers occurred both in app storage and direct raw Ollama output; do not claim usable Python |
| Lemonade Qwen3.6 thinking | Pass with temporary reasoning enabled | 1,209 thinking characters / 442 chunks; thinking TTFT 587 ms; answer TTFT 4,082 ms; saved answer `323` |
| Lemonade thinking cancellation / resend | Pass | Cancellation during Thinking; subsequent marker reply completed with separate reasoning metrics |
| Server switch Ollama → Lemonade | Reproduced defect | Global Ollama preference succeeded against Lemonade's compatibility API, exposing `:latest` model names and clearing the saved selection |

TTS callbacks were checked using the app's debug trace, not acoustic recording. Short marker replies had playback start/end callbacks. No new crash exit was observed during the baseline tests. This is bounded regression coverage, not a long-duration endurance claim.

The Lemonade server was originally running `--reasoning off --reasoning-budget 0`. Load-time-only overrides were used to exercise reasoning and the original arguments were restored, without `--save-options`. With a temporary budget of 512, one app reply contained `</think>` and duplicate answer text; a separate raw retry did not reproduce it. Root cause remains unproven. An unlimited-thinking retry completed cleanly. Do not hide this case by deduplicating adjacent text or silently deleting reasoning markers from arbitrary code.

## Follow-up correction

Store API preference per normalized base URL, using the legacy global preference only as a migration default. Apply the scoped preference to Settings validation and model discovery. Capture the request URL, cancel superseded discovery, and reject late results before they change models or selection. Selection refresh must use the URL whose models were fetched, rather than a mutable global Retrofit URL.

Regression tests cover a server supporting both APIs and a non-cancellable delayed response arriving after a different server has completed. Standard Debug unit tests: 1,697 passed, zero failed/skipped.

## Interrupted local work

The original tracked worktree changes (10 files, +348/-233 lines) were preserved without changing its working tree or index in `archive/streaming-wip-20260911`, commit `4c32c9c7`. This is an archive, not a tested release.

- Python syntax warning fixes: independently testable; candidate for a separate PR.
- Equal-adjacent-delta preservation and callback text-source priority: require runtime-specific verification.
- Local UI conflation, transient row identity, product Markdown mode and provisional NPU output gates: coupled changes; retain for focused follow-up, including cancellation/generation identity.
- TTS code-fence behavior changes: require explicit mixed prose/code playback regression testing.
- Per-chunk trace suppression: useful performance candidate, but preserve aggregate diagnostics.

Do not merge the archive wholesale. GPU output-cap validation remains a separate effort in PR #2554; no production cap increase is part of this server correction.
