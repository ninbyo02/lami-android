# QAIRT244 Native Patch Management

## 2026-07-02 GPU Prefill Preinvoke Diagnostic Patch

The prepared auxiliary native patch for the LiteRT-LM GPU Status 13 prefill
invoke investigation is:

```text
patches/qairt244_litertlm_gpu_prefill_preinvoke_diag.patch
```

This patch targets:

```text
runtime/executor/llm_litert_compiled_model_executor.cc
runtime/executor/llm_litert_compiled_model_executor.h
```

It logs one `ABSL_LOG(INFO)` line immediately before
`compiled_model_->RunAsync(...)` / `compiled_model_->Run(...)` in
`BindTensorsAndRunPrefill`. The marker is:

```text
qairt244_gpu_prefill_preinvoke_v1
```

The line includes selected signature/runner, executor/settings/sampler backend
identifiers, async mode, prompt/prefill token counts, prefill start/end
positions, current step, prefill/run input tensor counts and tensor
name/shape/byte/type summaries, and input/output KV-cache tensor counts and
shape summaries.

`build-qairt244-custom-jni` now applies this patch automatically after the
base 128 output / 128 input patch:

```text
patches/qairt244_litertlm_utf8_128token_128input.patch
patches/qairt244_litertlm_gpu_prefill_preinvoke_diag.patch
```

The forced command writes the diagnostic artifact with a label containing
`gpu_prefill_preinvoke_diag` and rejects the build if
`qairt244_gpu_prefill_preinvoke_v1` is missing from
`built_libs/liblitertlm_jni.so`.

For manual rebuilds, start from the fetchable LiteRT-LM tag `v0.11.0`
(`c87189528a758db32ead241f4fc9c64836398ee7`), apply the base patch first,
apply this patch second, then use the diagnostic label:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_128token_128input_gpu_prefill_preinvoke_diag
```

The generated `static_summary.md` reports the diagnostic label and a
`Diagnostic markers` table with both strings and readelf evidence. The same
marker is also included in `strings/liblitertlm_jni.so.filtered.txt` and
`readelf/liblitertlm_jni.so.rodata.txt`.

`scripts/check_qairt244_native_patch.sh` reports this patch as an auxiliary
`gpu_prefill_preinvoke_patch_status` without changing the active UTF-8 prompt
patch gate. Use `scripts/check_qairt244_native_patch.sh --selected-ref-check`
to create a temporary shared clone at `v0.11.0`, verify the base patch applies,
apply it, then verify the GPU prefill preinvoke patch applies after it.

## 2026-05-24 128 Output / 128 Input Hidden Template Phase

The current active native patch for the standard hidden qairt244 template
comparison is:

```text
patches/qairt244_litertlm_utf8_128token_128input.patch
```

This patch is a phase snapshot after the 128-token patch. It keeps
`max_output_tokens` bounded to `1..128` and adds a hidden-template prompt input
limit of 128 UTF-8 code points for the editable prompt entrypoint. It records
`native_prompt_input_code_point_limit=128` and
`native_prompt_input_limit_mode=hidden_template_experiment`.

The earlier 16/32/64/128-token patches remain checked in and must not be
deleted. They preserve the preceding output-token phases and the original
32-code-point prompt guard.

128 output / 128 input artifact:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt`

128 output / 128 input `liblitertlm_jni.so` sha256:
`4065d88c4788eaf28be140e133b7141783cad0698061c942b6942fa1fa886c2e`

128 output / 128 input JNI build log:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

`scripts/check_qairt244_native_patch.sh` now defaults to the 128 output / 128
input patch. Historical patches can still be checked by passing the patch path
as the second argument.

## 2026-05-24 128-Token Bounded Phase

The previous active native patch for the qairt244 UTF-8 internal prompt route is:

```text
patches/qairt244_litertlm_utf8_128token.patch
```

Historical phase patches remain checked in and must not be deleted:

```text
patches/qairt244_litertlm_utf8_16token.patch
patches/qairt244_litertlm_utf8_32token.patch
patches/qairt244_litertlm_utf8_64token.patch
```

The 16/32/64/128-token patches are ordered phase snapshots over the same
DEV-only editable/internal prompt route and the same external LiteRT-LM upstream
HEAD `c87189528a758db32ead241f4fc9c64836398ee7`. The 16-token patch introduced
the UTF-8 internal intent route, and the 32-, 64-, and 128-token patches raise
only the bounded native `max_output_tokens` guard and diagnostic limit for that
route. They do not promote `Backend.NPU`, add fallback, support generic/E4B/
qcs8275 models, or elevate the normal UI route.

The 128-token patch keeps the UTF-8 prompt validation contract unchanged:
empty prompts, NUL, invalid UTF-8, and prompts above 32 UTF-8 code points remain
rejected; non-ASCII UTF-8 remains allowed only for the internal intent route.

128-token artifact:
`artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt`

128-token `liblitertlm_jni.so` sha256:
`c3c04b274e3b090b1962b9e32b3f9467f2bad66707676e86463be939d37396f6`

128-token JNI build log:
`artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

## 2026-05-24 Historical 32-Token Bounded Phase

The historical 32-token native patch for the qairt244 UTF-8 internal prompt route is:

```text
patches/qairt244_litertlm_utf8_32token.patch
```

The previous 16-token patch remains checked in as historical reproduction
evidence:

```text
patches/qairt244_litertlm_utf8_16token.patch
```

Both patches are based on the same external LiteRT-LM upstream HEAD
`c87189528a758db32ead241f4fc9c64836398ee7`. The 32-token patch keeps the
UTF-8 prompt validation contract, keeps the 32-code-point prompt bound, rejects
NUL, invalid UTF-8, and empty prompts, and raises only the native
`max_output_tokens` guard and diagnostic limit from `16` to `32`.

`scripts/check_qairt244_native_patch.sh` now defaults to the 128 output / 128
input patch. To inspect the historical 16-, 32-, 64-, or 128-token patches
explicitly, pass a patch path as the second argument.

32-token artifact:
`artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt`

32-token `liblitertlm_jni.so` sha256:
`409008af863322e43ac35ffedec39bba64a8a9bd8a4859723fbf40e277dd3781`

32-token JNI build log:
`artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`


This project tracks the DEV-only qairt244 native LiteRT-LM change as a patch,
not as a checked-in binary artifact.

## Current State

- External checkout: `/home/sato/project/litert-custom-build/LiteRT-LM`
- Upstream HEAD used for the current reproducible qairt244 patch base:
  `v0.11.0` / `c87189528a758db32ead241f4fc9c64836398ee7`
- Native source:
  `kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`
- Active patch:
  `patches/qairt244_litertlm_utf8_128token_128input.patch`
- Historical patches:
  `patches/qairt244_litertlm_utf8_16token.patch`,
  `patches/qairt244_litertlm_utf8_32token.patch`,
  `patches/qairt244_litertlm_utf8_64token.patch`, and
  `patches/qairt244_litertlm_utf8_128token.patch`
- Current checkout state: 128 output / 128 input patch already applied as a
  dirty native source change.
- 128 output / 128 input artifact:
  `artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt`
- 128 output / 128 input `liblitertlm_jni.so` sha256:
  `4065d88c4788eaf28be140e133b7141783cad0698061c942b6942fa1fa886c2e`
- 128-token artifact:
  `artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt`
- 128-token `liblitertlm_jni.so` sha256:
  `c3c04b274e3b090b1962b9e32b3f9467f2bad66707676e86463be939d37396f6`
- Historical 32-token artifact:
  `artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt`
- Historical 32-token `liblitertlm_jni.so` sha256:
  `409008af863322e43ac35ffedec39bba64a8a9bd8a4859723fbf40e277dd3781`
- Historical 16-token artifact:
  `artifacts/litert_custom_build/20260524_144803_qairt244_16token_utf8prompt`
- Historical 16-token `liblitertlm_jni.so` sha256:
  `51e9a54c7ec32daabba7a6521ed378b8ebad72c4dfcd4597d6f4b0360e3ac947`

Do not reset the external checkout just to test this patch. The current dirty
state is intentional until the native change is moved to a fork or reapplied
from this patch in a clean checkout.

## Patch Scope

The patch is scoped to the DEV-only editable/internal prompt diagnostic route.
The 16/32/64/128-token patches preserve earlier bounded phases; the active
128 output / 128 input patch preserves the same route scope and does not
promote `Backend.NPU`, add fallback, or change normal app routing.

The patch records and guards:

- active `max_output_tokens` range `1..128`; historical 16/32/64/128-token
  patches remain available
- UTF-8 prompt acceptance for the hidden editable prompt route
- rejection of empty prompt, NUL byte, invalid UTF-8, and prompts above 128
  code points in `hidden_template_experiment` mode
- `native_prompt_validation_mode=utf8_hidden_template_experiment` for the
  hidden template input mode
- `native_prompt_input_code_point_limit=128`
- `native_prompt_input_limit_mode=hidden_template_experiment`
- `utf8_allowed=true`
- existing `QNN_HTP_V79_FastRPC_native_diag` evidence and decode timing output

## Apply Or Check

From the lami-android checkout:

```bash
scripts/check_qairt244_native_patch.sh
```

Expected output for the current external checkout is `status=applied`.

Manual checks:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch

git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --reverse --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

Interpretation:

- `apply --check` succeeds: patch is not applied and can be applied.
- `apply --check` fails and `apply --reverse --check` succeeds: patch is
  already applied.
- both fail: checkout differs from the patch and needs manual inspection.

To apply in a clean checkout:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

## Build And Record

Rebuild the active 128 output / 128 input native artifact from lami-android:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_128token_128input_gpu_prefill_preinvoke_diag
```

Record the produced artifact directory, native build log path, and JNI library
hash:

```bash
sha256sum <artifact>/built_libs/liblitertlm_jni.so
strings <artifact>/built_libs/liblitertlm_jni.so | grep -F qairt244_gpu_prefill_preinvoke_v1
readelf -p .rodata <artifact>/built_libs/liblitertlm_jni.so | grep -F qairt244_gpu_prefill_preinvoke_v1
grep -F qairt244_gpu_prefill_preinvoke_v1 <artifact>/static_summary.md
```

Never add `.so`, `.apk`, `.aar`, `.zip`, `.tar`, `.gz`, or `.litertlm` files to
Git. Commit the patch, docs, and source-level helper only.

## Fork Pin Versus Patch

Patch management is the lighter option for this phase because the change is
small, DEV-only, and still tied to a local QAIRT244 experiment. It keeps the
Android repo self-documenting and makes the dirty external checkout
reconstructable.

A fork pin is preferred once the native change must be shared across machines,
reviewed as native source, or reused for a later token phase. In that model,
record the fork URL and commit SHA in this document, then treat this patch as
historical reproduction evidence rather than the primary source of truth.
