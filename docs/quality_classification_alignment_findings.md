# Quality Classification Alignment Findings

Scope: investigation only. This document does not change Android runtime,
classifier logic, sanitizer logic, routes, native libraries, promotion gates, or
tests.

## Summary

Current repeatability:

```text
こんにちは -> quality_classification=template_artifact
あなたは誰ですか？ -> quality_classification=mixed_language
カレーの材料をお願いします。 -> quality_classification=natural_japanese

all three -> output_quality_candidate_status=quality_candidate_pass
```

The root cause of `quality_classification_alignment` is a two-layer quality
model:

1. `quality_classification` is a primary Unicode / template classifier produced
   before the S1 mapper's display-oriented candidate pass.
2. `output_quality_candidate_status` is a later S1 candidate evaluator that
   removes safe leading `>` / `<end_of_turn>` artifacts and checks the prepared
   display output.

These two layers intentionally answer different questions. The current blocker
is therefore mostly a classifier alignment problem, not evidence that the three
NPU outputs are unusable.

## Code Path

### Primary classifier

Primary `quality_classification` is produced in:

```text
app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244OutputUnicodeDiagnostics.kt
```

The entry points are:

```kotlin
Qairt244OutputUnicodeDiagnostics.buildFields(...)
Qairt244OutputUnicodeDiagnostics.buildFieldsFromExistingValues(...)
```

The classification function is:

```kotlin
private fun classifyQuality(output: String, codePoints: List<Int>): String
```

This debug classifier is attached by debug NPU paths such as:

```text
app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt
app/src/debug/java/io/github/ninbyo02/lami/npu/StandardHiddenQairt244PromptReceiver.kt
```

### Standard S1 mapping

The standard-route S1 mapper receives `qualityClassification` from raw
diagnostics and mostly preserves it:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper.kt
```

`NpuStandardRouteS1Mapper.map(...)` only overrides the quality classification
when raw role contamination is found in an otherwise success-like result. In the
normal case it copies:

```kotlin
qualityClassification = raw.qualityClassification
```

### Candidate evaluator

`output_quality_candidate_status` is computed separately in:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics.kt
```

The evaluator is:

```kotlin
evaluateNpuS1PersistentCustomJniQualityCandidate(...)
```

It is exposed by `NpuStandardRouteS1Result.outputQualityCandidateStatus`,
`preparedOutput`, and `actualDisplayText` in:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Contract.kt
```

The S1 success criterion already allows:

```kotlin
qualityClassification == "natural_japanese" || candidatePassed
```

Promotion scripts deliberately remain stricter and still require the primary
classification to align before standard route connection.

## Primary Classification Conditions

From `Qairt244OutputUnicodeDiagnostics.classifyQuality(...)`:

### `template_artifact`

Assigned when raw output contains any known template marker:

```text
<|im_start|>
<|im_end|>
<|start_header_id|>
<|end_header_id|>
<start_of_turn>
<end_of_turn>
[inst]
[/inst]
### system
### user
### assistant
system:
user:
assistant:
```

This check happens before Japanese / Latin language classification.

### `mixed_language`

Assigned when any of these are true:

- no Japanese-specific code point is present
- no Japanese text code point is present
- a non-Japanese script code point is present, such as Devanagari or Hangul
- a Latin word is present and is not allowed by the inline allowlist

The allowlist currently includes terms such as:

```text
ai, android, api, chatgpt, cpu, db, github, google, gpu, java,
javascript, kotlin, litert, npu, openai, python, qairt, qualcomm, qnn, sql, ui
```

Important consequence: `Google` alone is allowed, but `DeepMind` and `Gemma`
are not in the allowlist. A natural self-introduction containing `Google
DeepMind` and `Gemma 4` can therefore become `mixed_language`.

### `natural_japanese`

Assigned only after the artifact / mixed-language checks pass:

- output is not empty
- not repetitive circles
- no template artifact marker
- Japanese-specific and Japanese text code points are present
- no non-Japanese script code point is present
- all Latin words are allowed inline terms

## Candidate Pass Conditions

`evaluateNpuS1PersistentCustomJniQualityCandidate(...)` checks a prepared
display candidate, not just raw Unicode properties.

It:

- chooses `sanitizedOutput` if present, otherwise raw output
- removes safe `<end_of_turn>` variants
- removes a leading `>` from the prepared display candidate
- trims the prepared output
- for arithmetic prompts, extracts the answer
- fails on truly unsafe conditions such as placeholder leak, business-template
  leak, assistant repetition, Q/A continuation, special-token leak, user-turn
  leak, prompt repetition only, empty output, or arithmetic answer missing

It can pass with reason:

```text
natural_japanese_after_safe_leading_gt_and_end_of_turn_cleanup
```

That explains why a raw output can be classified as `template_artifact` while
the prepared display candidate passes.

## Mismatch Cases

Known or supported mismatch cases:

| Primary `quality_classification` | Candidate status | Why it can happen | Current interpretation |
| --- | --- | --- | --- |
| `template_artifact` | `quality_candidate_pass` | Raw output includes safe leading `>` / `<end_of_turn>` artifacts; prepared display text removes them. | Success-leaning, but primary classifier alignment is pending. |
| `mixed_language` | `quality_candidate_pass` | Output is natural Japanese with unallowlisted Latin proper nouns such as `DeepMind` / `Gemma`. | Success-leaning, but mixed-language gate needs review. |
| `natural_japanese` | `quality_candidate_pass` | Raw and prepared output both satisfy gates. | Aligned. |
| `unknown` or other non-natural class | `quality_candidate_fail` | Output is empty, unsafe, leaked, or otherwise not display-safe. | Real blocker. |

## Repeatability Explanation

### `こんにちは`

Observed:

```text
raw_output=>こんにちは！何かお手伝いできることはありますか？<end_of_turn>
sanitized_output=こんにちは！何かお手伝いできることはありますか？
actual_display_text=こんにちは！何かお手伝いできることはありますか？
quality_classification=template_artifact
output_quality_candidate_status=quality_candidate_pass
```

Explanation:

- Primary classifier sees `<end_of_turn>` in raw output and returns
  `template_artifact`.
- Candidate evaluator removes safe leading `>` and `<end_of_turn>`, producing a
  natural prepared/display output.
- This is a likely false positive for promotion blocking if judged solely by
  display quality, but it still needs gate alignment because raw artifacts are
  present.

### `あなたは誰ですか？`

Observed:

```text
sanitized_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。
actual_display_text=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。
quality_classification=mixed_language
output_quality_candidate_status=quality_candidate_pass
```

Explanation:

- The Japanese sentence is natural, and candidate output passes.
- Primary classifier sees Latin words.
- `Google` is allowlisted, but `DeepMind` and `Gemma` are not, so the output can
  be classified as `mixed_language`.
- This is likely a proper-noun false positive rather than NPU output corruption.

### `カレーの材料をお願いします。`

Observed:

```text
quality_classification=natural_japanese
output_quality_candidate_status=quality_candidate_pass
```

Explanation:

- No template markers remain.
- Japanese text is present.
- No non-Japanese script or unallowlisted Latin proper noun is present.
- Primary and candidate gates are aligned.

## Blocker Assessment

`quality_classification_alignment` is a real promotion blocker because the
strict gate still requires:

```text
quality_classification=natural_japanese
```

However, the current evidence suggests the blocker is not caused by poor
visible NPU output in the three known prompts. It is caused by conservative
primary classification:

- safe template residue in raw output is not distinguished from unsafe template
  leakage;
- Latin proper nouns are treated as mixed-language unless they are allowlisted.

So this is best classified as:

```text
classifier_alignment_needed
```

not:

```text
npu_output_quality_failure
```

## Recommended Response

Do not change runtime, sanitizer, route behavior, or promotion conditions in the
current phase.

Recommended next steps:

1. Keep `READY_FOR_STANDARD_ROUTE=false` while the primary classifier and
   candidate gate disagree.
2. Add a focused classifier-design review before any route connection work.
3. Decide whether safe raw template markers should become a separate primary
   class, for example `safe_template_cleanup_candidate`, rather than generic
   `template_artifact`.
4. Decide whether proper nouns such as `DeepMind` and `Gemma` should be
   allowlisted or handled by a separate `mixed_language_proper_noun_candidate`
   class.
5. Keep the full promotion gate strict until the same repeatability matrix
   reports aligned `natural_japanese` or an explicitly approved equivalent
   primary class.

No code change is recommended from this investigation alone.
