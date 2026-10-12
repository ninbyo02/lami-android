# Gemma 4 E2B SM8750 local image input

## Supported route

- Confirmed desktop bundle: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, 3,016,294,400 bytes,
  SHA-256 `41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e`.
- Language generation: `Backend.NPU(nativeLibraryDir)`.
- Vision encoder and adapter: `Backend.CPU()`.
- Image preprocessing: EXIF-aware decode, max 1280 pixels on the long edge, JPEG quality 85,
  up to 10 attachments per message.

This is a hybrid CPU/NPU route. Static bundle inspection found vision encoder,
adapter, and end-of-vision sections. The vision encoder and adapter operator tables
use built-in TFLite operators with no custom QNN dispatch op; QNN strings elsewhere
in the bundle are not enough to classify the image path as NPU-only.

## App behavior

Local image requests require Developer access because live device inference is still
unmeasured. The supported SM8750 bundle enters the Kotlin Conversation route with
CPU vision and NPU language generation. Generic Gemma vision can still be tried only
through the existing unverified developer-gated local image path.

Image-only requests use `この画像について説明して。`. Prepared images are saved under
app-private storage for chat history, and restored user turns keep `Content.ImageFile`
instead of degrading into text-only context.

An image or image-history failure is finalized as a failed response. It does not enter
the text-only GPU/CPU retry or canned-greeting fallback path.

## Validation

The static model conclusion above is not a device result. ADB/device image inference,
memory, quality, cancellation, and latency still need to be measured before this can
be treated as production-ready.

Run a standard debug build with the existing QAIRT native runtime, select the SM8750
bundle with NPU local inference and Developer access, attach a recognizable photo,
then check that the answer depends on the image and diagnostics include
`image_input_count`, `vision_backend=CPU`, and `language_backend=NPU`.
