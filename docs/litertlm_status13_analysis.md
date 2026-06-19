# LiteRT-LM Status 13 Invoke Failure Analysis

## Scope

This trace is scoped to LiteRT-LM runtime and Kotlin/JNI send paths around:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc`
- `INTERNAL: ... Failed to invoke the compiled model`
- Status Code 13, which is `absl::StatusCode::kInternal`

Out of scope: QAIRT/QNN/NPU loading, dispatch runtime setup, HTP library staging, and delegate creation.

## Key Source Boundary

The observed line near `llm_litert_compiled_model_executor.cc:735` is the prefill invoke boundary:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:713-739`
  duplicates prefill input tensors, appends KV-cache inputs, prepares KV-cache outputs, clears output events, then calls either `compiled_model_->RunAsync(...)` or `compiled_model_->Run(...)`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:733-735`
  is the async prefill call. A non-ok LiteRT status here is wrapped by `LITERT_RETURN_IF_ERROR`, producing the generic source-line error.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:917-949`
  has the same pattern for decode, always using `compiled_model_->RunAsync(kDecodeSignatureRunner, ...)`.

For the cited log line, prefill is the best match. Decode can produce the same generic symptom in nearby source revisions, but not at this exact cited prefill line.

## Send Path To Invoke

- `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt:95-156`
  synchronous `sendMessage(text)` wraps text as `Contents.of(text)` and then `Message.user(contents)`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt:172-228`
  callback async path does the same wrapping before JNI.
- `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc:2536-2637`
  `nativeSendMessageAsync` parses the message JSON and starts `Conversation::SendMessageAsync`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc:2640-2665`
  `nativeSendMessage` parses the same message JSON and calls `Conversation::SendMessage`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:434-584`
  `Conversation::SendMessageAsync` renders one turn, converts it to `InputData`, schedules `session_->RunPrefillAsync`, and only starts decode after prefill succeeds.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:338-390`
  `SessionBasic::RunPrefillAsync` preprocesses text/modality contents, then calls `PrefillInternal(..., wait_for_completion=false)` on a worker.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:284-292`
  `PrefillInternal` combines contents into `ExecutorInputs` and calls the executor prefill pipeline.

## Ranked Likely Failure Mechanisms

### P0: Prefill input length and compiled prefill signature mismatch

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/litert_compiled_model_executor_utils.cc:217-243`
  builds a map of available prefill signatures from each signature's `positions`/`input_pos` tensor length.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/litert_compiled_model_executor_utils.cc:246-275`
  splits an input token sequence into work groups, using the largest prefill runner until the remainder fits a smaller one.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1431-1456`
  creates prefill buffers for each selected work group and calls `PrefillInternal` with the selected length.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:363-439`
  resizes dynamic tensors and creates token/embedding, positions, attention-mask, and param buffers based on `sequence_length` and `context_length`.

Likely mechanism:

If prompt rendering/tokenization produces a token count that selects a signature or dynamic shape not actually accepted by the compiled model backend, the executor may still reach `compiled_model_->RunAsync` with buffers whose shapes/names are accepted at creation time but rejected by invoke. This is the strongest match for an error exactly at prefill `RunAsync`.

Prompt and template changes matter because they directly change token count and chunking. The first send includes rendered user/template text, not the raw UI string.

### P1: KV-cache capacity, current step, and small max-token configuration

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:671-681`
  fills attention mask and the GPU single-buffer cache param with `start_step` and `ids.size()`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/litert_compiled_model_executor_utils.cc:317-336`
  writes `{start_index, end_index, end_index}` to `param_tensor`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/litert_compiled_model_executor_utils.cc:339-388`
  fills attention-mask windows for `start_timestep + steps`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:270-276`
  `max_output_tokens` only affects decode config, while engine/session token capacity is configured earlier.

Likely mechanism:

If engine/session token capacity is smaller than the rendered prompt prefill requirement, or if current step plus update length exceeds the model's expected KV/cache/mask dimensions, the invoke can fail after all buffers are bound. Small values used as engine max-token limits are especially risky because LiteRT-LM uses those limits for runtime/cache shape, not just user-visible output length.

### P2: Text-only run through a multimodal Gemma 4 processor creates hidden modality-sensitive tokens

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/model_data_processor/gemma4_data_processor.cc:338-428`
  scans rendered prompts for image/audio delimiters and converts matching content into `InputImage`, `InputAudio`, and end tokens.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:148-248`
  combines text token IDs and encoded modality embeddings into one `ExecutorInputs`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:189-197`
  inserts `ExecutorVisionData::kSpecialToken` once per image embedding token.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:213-219`
  inserts audio special tokens and audio end tokens.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/components/embedding_lookup/embedding_lookup_manager.cc:69-105`
  rejects provided multimodal embeddings if the embedding manager was not initialized for full multimodal support.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/components/embedding_lookup/embedding_lookup_manager.cc:147-173`
  maps negative modality tokens to modality embeddings or default text embedding during prefill.

Likely mechanism:

Even a text-facing API can become modality-sensitive if the rendered prompt contains image/audio delimiters or if the message JSON contains typed content. Mismatched placeholders versus actual content fail before invoke with `InvalidArgument`, but a subtle mismatch in modality token count, embedding shape, or end-token handling can reach the compiled prefill model with inconsistent token/embedding buffers.

### P3: Input token tensor shape differs from what static prefill accepts

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.h:59-64`
  documents prefill input token IDs as shape `[batch, sequence_length]`.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1411-1420`
  static prefill accepts batch size `1` or `output_heads`, then requires non-empty sequence length.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1426-1430`
  flattens token IDs and reduces to the first batch element before work-group selection.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:242-248`
  creates the final token tensor from combined token IDs.

Likely mechanism:

If Java/JNI/session preprocessing supplies a token tensor with unexpected batch dimension, empty second dimension, or a flat layout that does not match the compiled signature assumptions, executor checks usually fail before invoke. If the shape passes those checks but does not match the compiled runner's stricter runtime layout, it can surface at `RunAsync`.

### P4: Async prefill scheduling hides the original failing phase

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:540-584`
  decode is only scheduled after prefill callback reports done.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:377-390`
  async worker reports the prefill status through callback.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:381-420`
  synchronous `SendMessage` waits for session completion and then returns callback error status.

Likely mechanism:

The Java exception says `nativeSendMessage`, but the cited executor line means the error likely happened inside asynchronous prefill before decode was started. This can look like a send-message failure at the app boundary while still being a first-prefill invoke failure.

### P5: Sampler settings mainly affect decode, but can affect later decode invoke shape

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/Config.kt:116-155`
  conversation/session sampler config can be null, in which case engine defaults are used.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1231-1283`
  executor initializes an internal sampler and may allow the sampler to handle decode input tensors.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1298-1313`
  sampler input handling installs `BindTensorsAndRunDecodeStatic` as an inference callback.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1315-1339`
  sampling may swap decode input tensors before sampling.

Likely mechanism:

Sampler config is unlikely to cause the line-735 prefill invoke directly. It is relevant for failures at decode invoke (`llm_litert_compiled_model_executor.cc:945-949`) because sampler input handling can trigger decode through a callback and changes which decode input tensors are bound.

### P6: Prompt template and history delta can produce invalid or unexpectedly long single-turn text

Evidence:

- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:191-247`
  full-history rendering checks that the new rendered template begins with the previous rendered string and sends only the delta.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/conversation.cc:250-267`
  single-turn rendering is preferred only if the prompt template supports it; otherwise full-history delta rendering is used.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_utils.cc:46-81`
  rendered strings are tokenized into `InputText` tensor buffers.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_utils.cc:168-218`
  empty text chunks are skipped.

Likely mechanism:

Incorrect prompt-template selection, appended-message state, history delta rendering, or extra-context substitution can produce a longer or structurally different token sequence than expected. Template mismatch errors usually fail before invoke, but a valid-yet-oversized prompt is a plausible prefill invoke failure.

## Lower-Probability Pre-Invoke Errors

These are important diagnostics but do not match the exact line-735 generic invoke message unless swallowed/rethrown elsewhere:

- Missing input tokens/embeddings: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:380-389`
- Empty prefill token IDs: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1419-1420`
- Work groups not covering input: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:1458-1459`
- Unsupported attention-mask type/rank: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/litert_compiled_model_executor_utils.cc:278-314` and `:339-344`
- Missing or extra Gemma 4 image/audio content for rendered placeholders: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/conversation/model_data_processor/gemma4_data_processor.cc:383-423`
- Empty session input: `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/session_basic.cc:296-299` and `:338-343`

## Current Best Read

The strongest evidence points to a first-prefill invoke failure caused by runtime input shape/capacity mismatch, not model loading. The highest-value discriminators are:

1. Log rendered prompt byte length and token count after `ToInputDataVector`/`PreprocessContents`.
2. Log selected prefill work groups and signature names from `GetOptimizedPrefillWorkGroups`.
3. Log created prefill tensor names, layouts, and packed sizes immediately before `BindTensorsAndRunPrefill`.
4. Run one Gallery-parity text path: `Contents.of(Content.Text(prompt))`, null/non-enabled image and audio content, Gallery-equivalent sampler config, and production-scale engine token capacity.
5. Treat sampler changes as decode-only unless the failing line moves from prefill `RunAsync` to decode `RunAsync`.
