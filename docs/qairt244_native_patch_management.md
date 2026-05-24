# QAIRT244 Native Patch Management

This project tracks the DEV-only qairt244 native LiteRT-LM change as a patch,
not as a checked-in binary artifact.

## Current State

- External checkout: `/home/sato/project/litert-custom-build/LiteRT-LM`
- Upstream HEAD used for this patch:
  `c87189528a758db32ead241f4fc9c64836398ee7`
- Native source:
  `kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`
- Patch:
  `patches/qairt244_litertlm_utf8_16token.patch`
- Current checkout state: patch already applied as a dirty native source change.
- 16-token artifact:
  `artifacts/litert_custom_build/20260524_144803_qairt244_16token_utf8prompt`
- `liblitertlm_jni.so` sha256:
  `51e9a54c7ec32daabba7a6521ed378b8ebad72c4dfcd4597d6f4b0360e3ac947`

Do not reset the external checkout just to test this patch. The current dirty
state is intentional until the native change is moved to a fork or reapplied
from this patch in a clean checkout.

## Patch Scope

The patch is scoped to the DEV-only editable/internal prompt diagnostic route.
It preserves the 16-token phase and does not promote `Backend.NPU`, add
fallback, or change normal app routing.

The patch records and guards:

- `max_output_tokens` range `1..16`
- UTF-8 prompt acceptance for the internal intent route
- rejection of empty prompt, NUL byte, invalid UTF-8, and prompts above 32 code
  points
- `native_prompt_validation_mode=utf8_internal_intent`
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
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_16token.patch

git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --reverse --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_16token.patch
```

Interpretation:

- `apply --check` succeeds: patch is not applied and can be applied.
- `apply --check` fails and `apply --reverse --check` succeeds: patch is
  already applied.
- both fail: checkout differs from the patch and needs manual inspection.

To apply in a clean checkout:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_16token.patch
```

## Build And Record

Rebuild the native artifact from lami-android without changing the 16-token
phase:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_16token_utf8prompt
```

Record the produced artifact directory, native build log path, and JNI library
hash:

```bash
sha256sum <artifact>/jni/arm64-v8a/liblitertlm_jni.so
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
