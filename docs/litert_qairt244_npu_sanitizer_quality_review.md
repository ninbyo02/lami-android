# QAIRT244 NPU Sanitizer Quality Review

Date: 2026-05-31

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, change prompt templates, change decode
logic, or run promote.

## Current Case

Observed S1 real prompt failure:

```text
input=こんばんは
raw_output_preview=૩です|
sanitized_output_length=0
status=failure
reason=empty_after_sanitize
quality_classification=mixed_language
run_decode_reached=true
timeout=false
fallback=false
fresh_crash=false
tts=not invoked
```

This means:

- ChatScreen entered the NPU standard route.
- The real prompt reached the provider.
- Native decode was reached.
- The backend did not fallback.
- The run did not timeout or fresh-crash.
- The raw output was already malformed before user-facing display.
- The sanitizer rejected the output, producing an empty sanitized string.

The current behavior is conservative and intentionally avoids showing or
speaking malformed mixed-language output.

## 1. `empty_after_sanitize` Conditions

`empty_after_sanitize` is produced in the dev-only route adapter when:

```text
native_result == success
and
sanitized_output == ""
```

The adapter flow is:

```text
raw native output
-> Qairt244NpuOutputSanitizer.sanitize(rawOutput, prompt)
-> sanitizedOutput
-> success = nativeSuccess && sanitizedOutput.isNotEmpty()
```

If native succeeds but the sanitizer removes or rejects every displayable
segment, the result is classified as:

```text
status=failure
reason=empty_after_sanitize
```

For `こんばんは`, this is not an NPU reachability failure. It is a display
quality failure after decode.

## 2. `mixed_language` Conditions

`quality_classification` is currently derived from raw output diagnostics before
sanitizer acceptance.

The current classification is broadly:

- empty output -> `empty_output`
- single question mark -> `single_question_mark`
- repeated circles -> `repetitive_circles`
- known template artifacts -> `template_artifact`
- no Japanese code points -> `mixed_language`
- at least 3 Latin letters -> `mixed_language`
- otherwise -> `natural_japanese`

The `こんばんは` raw preview includes a non-Japanese character and symbols:

```text
૩です|
```

It contains Japanese text (`です`) but also a Gujarati digit-like character and
pipe punctuation. The current classifier reports this as `mixed_language` in
the runtime trace. This classification is useful and should not be hidden by
sanitizer fallback.

## 3. Handling Broken Raw Output

When raw output contains non-Japanese script, symbols, or broken characters, the
safe default should remain:

```text
do not display it as assistant text
do not persist it as a normal assistant answer
do not feed it to TTS
keep diagnostics visible in dev-only surfaces
```

The sanitizer should not accept fragments such as:

```text
૩です|
3؟です|
```

as user-facing text simply because they contain a Japanese substring.

Recommended strict failure buckets:

| Bucket | Handling |
| --- | --- |
| replacement chars present | hard fail |
| disallowed control chars | hard fail |
| no Japanese code points | hard fail |
| non-Japanese script mixed with short Japanese fragment | hard fail unless a clean Japanese sentence can be extracted |
| template/role remnants only | fail or extract only after explicit boundary logic |
| exact non-greeting prompt echo | fail |
| standalone known greeting response | allow only if raw output is exactly the clean greeting response |

## 4. Keep The Conservative Empty Policy?

Recommendation: keep the current "unsafe raw becomes empty" policy for the
standard route.

Reasons:

- The S1 result contract requires `sanitizedOutput.isNotBlank()`.
- S1 success also requires `qualityClassification == natural_japanese`.
- S2 DB, S3 Markdown, S4-A pseudo streaming, and S5 TTS depend on S1 success.
- Displaying malformed mixed-language fragments would make later phases harder
  to reason about.
- The current failure is visible through the debug block and trace without
  promoting broken output.

The change should be diagnostic/classification first, not permissive display.

## 5. Fallback Response Option

One possible user-facing fallback:

```text
すみません、うまく応答を生成できませんでした。
```

There are two ways to model it.

### Option A: Transient Failure Fallback

Show a fallback line in the debug/temporary S1 display when:

```text
run_decode_reached=true
fallback=false
timeout=false
fresh_crash=false
sanitized_output=""
reason=empty_after_sanitize
```

Do not mark S1 success. Keep:

```text
successCriteriaMet=false
quality_classification=mixed_language
```

Pros:

- User sees an understandable failure instead of a blank area.
- DB/Markdown/Streaming/TTS stay off because S1 did not succeed.
- Minimal risk of treating synthetic fallback as model output.

Cons:

- The UI must clearly distinguish fallback text from NPU output.

### Option B: Synthetic Assistant Result

Replace empty sanitized output with fallback text and mark it as success.

This is not recommended now.

Problems:

- It would let S2 persist a synthetic answer as if it were NPU output.
- S3/S4-A/S5 could process a fallback answer as normal content.
- It would hide raw decode quality regressions.

Recommendation: if fallback text is added, use Option A.

## 6. Should Fallback Be Spoken By TTS?

Recommendation: do not TTS fallback responses in the first implementation.

Current S5 mapper behavior already blocks TTS when:

```text
s1Result.successCriteriaMet=false
```

For `empty_after_sanitize`, S1 fails because:

- `status=failure`, or
- `reason != success`, or
- `sanitizedOutput.isBlank()`, or
- `qualityClassification != natural_japanese`.

Therefore `NpuStandardRouteS5TtsMapper` returns:

```text
failureReason=s1_success_criteria_not_met
ttsCandidate=null
```

If a transient fallback line is shown, it should not be passed into S5. A
separate future setting could allow speaking failure notices, but that should be
explicit and not tied to NPU answer TTS.

## 7. S5 TTS Candidate Relationship

The S5 candidate should continue to require real S1 success:

```text
status=success
reason=success
run_decode_reached=true
npuBackendEvidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
timeout=false
fresh_crash=false
sanitizedOutput nonblank
quality_classification=natural_japanese
```

Do not generate TTS candidates for:

- `empty_after_sanitize`;
- `mixed_language`;
- fallback display text;
- punctuation-only output;
- sanitizer diagnostics;
- raw output previews.

This keeps TTS aligned with actual accepted assistant content.

## 8. Sanitizer Improvement Plan

Recommended implementation order for a later change:

1. Add sanitizer reason diagnostics without changing acceptance:
   - `empty_reason=non_japanese_or_mixed_fragment`
   - `empty_reason=prompt_echo_only`
   - `empty_reason=template_artifact_only`
   - `empty_reason=role_remnant_only`
   - `contains_non_japanese_script=true`
   - `contains_disallowed_symbol=true`

2. Add targeted extraction only for safe Japanese text:
   - extract a complete Japanese sentence when surrounded by role remnants;
   - reject if the extracted text is only a tiny fragment like `です`;
   - reject if non-Japanese script remains in the same candidate segment.

3. Keep `mixed_language` visible in debug output even if a safe segment is later
   extracted, for example:

```text
raw_quality_classification=mixed_language
sanitized_quality_classification=natural_japanese
```

4. Add unit tests for:
   - `こんばんは` broken fragment stays empty;
   - `こんにちは。` remains accepted;
   - `ユーザー:` / `アシスタント:` remnants are removed or used as boundaries;
   - fallback display text is not treated as sanitizer output.

## 9. Rollback

Runtime rollback:

```text
Settings -> NPU標準ルート -> OFF
```

Code rollback for sanitizer diagnostics:

- remove new sanitizer diagnostic fields;
- keep old empty behavior.

Code rollback for fallback display:

- hide the fallback UI branch;
- continue showing only S1 debug diagnostics.

No native, prompt-template, decode, DB, Markdown, pseudo-streaming, TTS, or
Backend.NPU persistence rollback should be needed.

## 10. Runtime Check Plan

After any sanitizer diagnostic change, run only short S1 checks first:

```text
こんにちは
こんばんは
こんばんは。
こんばんわ
おはよう
ありがとう
あ
q
え
```

For each case capture:

- `raw_output_preview`
- `raw_output_length`
- `sanitized_output_preview`
- `sanitized_output_length`
- `quality_classification`
- sanitizer reason diagnostics
- `status`
- `reason`
- `run_decode_reached`
- `timeout`
- `fallback`
- `fresh_crash`

Pass conditions:

- `こんにちは` remains natural Japanese success.
- `こんばんは` broken output remains rejected unless a clearly valid Japanese
  sentence is produced.
- TTS remains silent for `empty_after_sanitize` and `mixed_language`.
- S2/S3/S4-A/S5 do not process fallback text as a normal answer.

Stop conditions:

- malformed raw output becomes a normal assistant message;
- fallback text is persisted to DB as model output;
- fallback text is spoken by S5 TTS;
- timeout/fallback/fresh crash appears.

## Recommendation

Do not loosen the sanitizer to pass broken fragments. The next safe step is to
add sanitizer empty-reason diagnostics and optionally a transient, non-TTS,
non-persisted fallback notice for `empty_after_sanitize`. Template and decode
changes should remain separate follow-up experiments.
