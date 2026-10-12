# Gemma 4 E2B local image input on CPU, GPU, and NPU

## Supported route

- Confirmed desktop bundle: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, 3,016,294,400 bytes,
  SHA-256 `41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e`.
- Confirmed generic bundle: `gemma-4-E2B-it.litertlm`, 2,588,147,712 bytes,
  SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
- Image preprocessing: EXIF-aware decode, max 1280 pixels on the long edge, JPEG quality 85,
  up to 10 attachments per message.

| Selected backend | Model slot / bundle | Language | Vision encoder | Adapter |
| --- | --- | --- | --- | --- |
| CPU | Generic / `gemma-4-E2B-it.litertlm` | CPU | CPU | CPU |
| GPU | Generic / `gemma-4-E2B-it.litertlm` | GPU | GPU | CPU |
| NPU / Qualcomm QNN | NPU / SM8750 bundle | NPU | CPU | CPU |
| Automatic | Selected generic bundle | GPU | GPU | CPU |
| Automatic | Selected SM8750 bundle and available NPU route | NPU | CPU | CPU |

The NPU setting is a hybrid CPU/NPU route, not all-NPU image processing.
These are requested configurations, not measured per-operator accelerator results.
Static bundle inspection found vision encoder,
adapter, and end-of-vision sections. The vision encoder and adapter operator tables
use built-in TFLite operators with no custom QNN dispatch op; QNN strings elsewhere
in the bundle are not enough to classify the image path as NPU-only.

## App behavior

Local image requests require Developer access because live device inference is still
unmeasured. Exact size and SHA-256 identify the two supported bundles. A mismatch
between model and explicit backend is rejected: the NPU-compiled language bundle
does not silently run as CPU/GPU, and the generic bundle does not silently handle
an NPU selection. The selected path and backend are captured at send time and checked
again before inference, including follow-ups that only contain image history.

CPU/GPU image turns use the existing typed Conversation API with a fresh engine;
the previous text-only resident engine is released first. This avoids reusing an
engine initialized without vision. Image capacity includes bounded restored history,
even with no new attachment. Engine and conversation cleanup, including native
cancellation, uses the existing official API implementation. NPU turns retain the
existing resident Kotlin Conversation route and its lifecycle.

Image-only requests use `この画像について説明して。`. Prepared images are saved under
app-private storage for chat history, and restored user turns keep `Content.ImageFile`
instead of degrading into text-only context.

An image or image-history failure is finalized as a failed response. It does not enter
the text-only GPU/CPU retry or canned-greeting fallback path.

## Validation

Based on main merge `4a09c4550c7f1c4dacf7f6ff315fa18ae7b36454`, the final source passed
`:app:compileStandardDebugKotlin`, `:app:compileStandardDebugAndroidTestKotlin`, and
269 related `:app:testStandardDebugUnitTest` tests with zero failures, errors, or
skips. The new backend-policy tests cover model/backend compatibility, CPU/GPU
EngineConfig values, restored image capacity, and separate NPU/CPU diagnostics.
Android test sources were compiled; no instrumented tests were executed.

The static model conclusion above is not a device result. ADB/device image inference,
memory, quality, cancellation, and latency still need to be measured before this can
be treated as production-ready.

No device was operated or APK installed for this change; the shared device remains
reserved for the separate TTS measurement task. Image validation stays with the
integration owner.

When that owner has device access, run a standard debug build with the existing
native runtime and Developer access. For CPU and GPU select the generic bundle in
the generic model slot. For NPU select the SM8750 bundle in the NPU model slot.
Use the same recognizable photo and short question in each mode. Repeat with no
text, follow up without another attachment, reopen the chat, switch backend, and
stop a request. Confirm semantic image dependence and inspect `image_input_count`,
`language_backend`, `vision_backend`, and `vision_adapter_backend` separately.

Local JVM tests check backend/model compatibility, requested EngineConfig values,
typed image content, history capacity, and failure policy. They do not prove native
image inference or accelerator execution on a device.
