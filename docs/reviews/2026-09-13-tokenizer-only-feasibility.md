# Tokenizer-only counting feasibility

## Decision

Standalone counting without inference-engine initialization is technically feasible for the inspected Gemma 4 E2B model copies. A host probe loaded only their SentencePiece section and encoded text, with no LiteRT/MediaPipe engine import or generation. This qualifies feasibility, not Android integration or equivalence with the installed model/runtime.

Recommend a separately built, hidden-symbol tokenizer-only JNI adapter, initially diagnostic-only, then promote only after exact original-text counts and resource measurements pass. Do not replace the existing coherent GPU/NPU libraries or reuse raw internal engine/tokenizer handles.

PR #2604 was merged at `5db231d790d4e203c3f4732122fd4358eb13fe33` after both CI jobs succeeded. Its tree matches the tested `a9db81f101c739f4a161cccaae7d39055d376a98`.

## API/source evidence

- Inspected the locally resolved `litertlm-android:0.11.0` AAR with javap: Engine, Conversation, Session and LiteRtLmJni expose no standalone tokenizer/count API. Reflection cannot call an API that is absent.
- Inspected `tasks-genai:0.10.33` AAR: LlmInference.sizeInTokens resets/creates an implicit session; LlmInferenceSession.sizeInTokens validates session state and calls LlmTaskRunner. getSentencePieceProcessorHandle is an instance method backed by that runner, not an independent loader. Retaining its raw pointer after destroying its owner is not a supported solution.
- In pinned upstream LiteRT-LM v0.11.0, [ModelResourcesLitertLm.GetTokenizer](https://github.com/google-ai-edge/LiteRT-LM/blob/c87189528a758db32ead241f4fc9c64836398ee7/runtime/components/model_resources_litert_lm.cc) selects embedded SentencePiece first, or HuggingFace JSON if supported. This operation does not require creating an inference executor.
- [SentencePieceTokenizer](https://github.com/google-ai-edge/LiteRT-LM/blob/c87189528a758db32ead241f4fc9c64836398ee7/runtime/components/sentencepiece_tokenizer.cc) constructs SentencePieceProcessor from serialized model bytes; TextToTokenIds calls Encode. [LitertLmLoader](https://github.com/google-ai-edge/LiteRT-LM/blob/c87189528a758db32ead241f4fc9c64836398ee7/runtime/util/litert_lm_loader.h) exposes the tokenizer section separately.
- File layout follows the [pinned schema](https://github.com/google-ai-edge/LiteRT-LM/blob/c87189528a758db32ead241f4fc9c64836398ee7/schema/core/litertlm_header_schema.fbs) and [reader](https://github.com/google-ai-edge/LiteRT-LM/blob/c87189528a758db32ead241f4fc9c64836398ee7/schema/core/litertlm_read.cc).

Context7 supplied discovery pointers; conclusions were checked against pinned source and actual resolved AARs. Current upstream also offers later versions, but no dependency upgrade is proposed or required for this finding.

## Host proof

Ubuntu development PC, Python SentencePiece 0.2.1, FlatBuffers 25.2.10, isolated temporary virtual environment. Read only the file header and tokenizer section, not model weights. No inference engine initialized and no phone accessed.

| Local model | File bytes | SP section offset | SP bytes | One observed host tokenizer construction |
|---|---:|---:|---:|---:|
| gemma-4-E2B-it.litertlm | 2,588,147,712 | 32,768 | 4,689,013 | 51.82 ms |
| gemma-4-E2B-it_qualcomm_sm8750.litertlm | 3,016,294,400 | 3,011,592,192 | 4,689,013 | 36.21 ms |

Both section hashes are `e594c8a90eb08d8bda498ff4747977dc827ae0c3c56b5c0d41a605a22d02ef03`; vocabulary size 262,144. Six short text encodes together took about 0.09–0.10 ms after construction. These are single host observations, not phone latency, cold-storage benchmarks or a claimed Android speedup. Serialized section size is not resident memory consumption.

Important identity limit: the installed GPU model previously measured 2,583,085,056 bytes, different from the inspected PC copy. Matching NPU file size alone also does not verify whole-model identity. Read the installed tokenizer section and compare hashes before promotion.

A read-only comparison against saved device display responses gave counts 5/1/2 for greeting/short/resend, matching historical device raw-text counts. Display long text (835 characters) counted 454 while the historical raw result (841 characters) counted 455. This is not a same-input equivalence test. Preserve DeferredTokenizerInput original prompt and output; do not compare sanitized display strings or claim this discrepancy proves tokenizer incompatibility. Input-template/special-token behavior still needs paired testing.

## Proposed implementation boundary and acceptance

1. Add a tokenizer-only adapter with open/count/close and deterministic error results. Compile SentencePiece privately with hidden symbols and bounded parsing; avoid linking the full execution runtime or sharing undocumented native handles. Keep ABI/16-KB packaging checks.
2. Extract the selected model's SP section using verified container metadata. Validate version, file bounds, header/section sizes and integer arithmetic. HF-only models must use the existing fallback until separately supported; never guess tokenizer format from the filename.
3. Use the coordinator from #2604 for serialized ownership. Start with no native tokenizer residency; compare whether a small bounded tokenizer lifetime is worthwhile after measuring actual heap/PSS and cold/warm costs. Keep the existing result cache keyed by model identity and exact text.
4. In a diagnostic comparison, run old and candidate counters on identical original effective prompt/raw output, including whitespace, Japanese, mixed text, emoji, special tokens, empty input, long/code output and cancellation. Require equal input/output counts before adopting a model/runtime combination. Retokenized output length is not inherently the model's generated-token count or full KV usage.
5. Qualify the exact installed model, current runtime, normal/fallback paths, cancellation/cleanup and next-send overlap on device. Unknown model/tokenizer or errors retain the current fallback and truthful estimated status.

The Python probe is for trusted local files only; its assertions/FlatBuffers table access are not production validation. It is not packaged in the app. No native adapter was built, no APK installed, no Android latency/memory/power measurement performed in this investigation.

## Reproduction

Create an isolated venv, install `sentencepiece==0.2.1 flatbuffers==25.2.10`, then run:

```sh
python scripts/probes/inspect_tokenizer_only.py /path/to/model.litertlm
```

The companion JSON records observed section metadata, hashes, counts and timings. No model weights/tokenizer blob or private conversation text is committed.
