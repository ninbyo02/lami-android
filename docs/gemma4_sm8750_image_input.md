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

Local image requests require Developer access. Single-turn device inference has now
been measured, but image-history restoration and multi-image counting still fail
the checks below. Exact size and SHA-256 identify the two supported bundles. A mismatch
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
This was the source-only validation before the device measurements below.

The static bundle inspection is not per-operator accelerator profiling. Real photos,
image-only UI sends, chat reopening, backend switching, cancellation, and release
APK behavior remain unverified. Do not promote the route based only on synthetic
single-turn success.

For further integration checks, run a standard debug build with the existing
native runtime and Developer access. For CPU and GPU select the generic bundle in
the generic model slot. For NPU select the SM8750 bundle in the NPU model slot.
Use the same recognizable photo and short question in each mode. Repeat with no
text, follow up without another attachment, reopen the chat, switch backend, and
stop a request. Confirm semantic image dependence and inspect `image_input_count`,
`language_backend`, `vision_backend`, and `vision_adapter_backend` separately.

Local JVM tests check backend/model compatibility, requested EngineConfig values,
typed image content, history capacity, and failure policy. They do not prove native
image inference or accelerator execution on a device.

## Device measurements: 2026-10-12

Tests ran on NX733J / SM8750 over ADB, using isolated package
`io.github.ninbyo02.lami.npuvalidation.imagebench`; existing LAMI, assistantprobe,
and TTS APKs were not replaced. Source was `5b4ca7fdc` plus the manual benchmark
in this change. Debug API classes were LiteRT-LM 0.17.0, with the pinned custom
QAIRT runtime from `config/standard_gpu_npu_runtime.json`. Release is not covered.
The app APK SHA-256 was
`1c5254c5b3fb06996da996b45bfdf033d38a332c4819fe15c9416941fbe7c17e`.

Device SHA-256 verified `/data/local/tmp/lami_gpu_model.litertlm` against the
generic fingerprint above. `/data/local/tmp/lami_npu_durability_model.litertlm`
and `/sdcard/Download/ninbyo02-gemma-4-E2B-it-SM8750-NPU.litertlm` both matched
the SM8750 fingerprint above. The user-named file is byte-identical to the
Qualcomm-named bundle; its name alone does not establish separate quantization.
For CPU/GPU UI selection on this device, the matching Download file is
`gemma-4-E2B-it-edge-gallery.litertlm`. The similarly named Download file
`gemma-4-E2B-it.litertlm` is a different 2,583,085,056-byte version, SHA-256
`ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`;
it was not tested and is not in the app's current fingerprint allowlist.
The final manual test APK SHA-256 was
`b8107cc00d4827948815619f1e0d5c27605514664e2f3545d5b41e85bfc4be0d`.

Fixtures were 384x384 white-background bitmaps, passed through the app's JPEG
preprocessor. One image was a blue square. Multi-image input contained red circles
followed by a final blue square. SHA checks and preprocessing are outside the timer.
All times are individual trials, not medians; engines were fresh, but runtime/model
disk caches were not cleared. Total includes initialization and generation; CPU/GPU
and direct capacity probes include engine close, while the resident NPU product
route closes after its timer. Initial-token figures are visible first-partial time.

| Backend | 1 image: first / total (s) | 10 images: first / total (s) | 15 images: first / total (s) |
| --- | --- | --- | --- |
| CPU + CPU vision | 10.182 / 10.488 | 63.448 / 64.461 | 101.521 / 103.098 |
| GPU + GPU vision | 15.184 / 15.684 | 16.251 / 17.512 | 18.486 / 20.211 |
| NPU + CPU vision | 10.185 / 10.242 | 48.966 / 49.262 | 71.038 / 71.815 |

All three one-image routes correctly identified the blue square. Ten images were
accepted and generated text, but the question asking for nine red circles and one
blue square failed: CPU/GPU answered zero red circles; NPU answered four. A GPU
comparison with images before text answered five (16.484 s), so content ordering
alone did not resolve counting. Do not equate successful generation with accuracy.

Fifteen-image direct API capacity probes used images before text and a shorter
first/last-image question. All three correctly identified the first red circle and
last blue square. This checks batch acceptance and both endpoints, not independent
recognition of every middle image. These are diagnostic probes outside the app's
10-attachment limit, not evidence that the UI can select fifteen images.

GPU and SM8750 NPU sixteen-image probes both produced:
`Input token ids are too long. Exceeding the maximum number of tokens allowed: 4226 >= 4096`.
GPU rejection took 17.668 s; NPU rejection took 72.171 s. No larger batches were
attempted. CPU sixteen-image rejection was not separately measured.

Observed process PSS/RSS snapshots (not peaks) were 1.80/2.01 GiB for CPU fifteen
images, 1.32/1.54 GiB for GPU fifteen images, and 2.42/2.63 GiB for NPU sixteen
images before rejection. An NPU one-image first-partial sample was 2.41 GiB PSS;
its process had QNN HTP/V79 stub libraries and `/dev/fastrpc-cdsp` open. GPU's
image-first ten-image trial had the OpenCL provider and `/dev/kgsl-3d0`, with no
QNN HTP library in the filtered runtime snapshot. These are process/backend
evidence, not timing for individual operators. Vision/adapter remain CPU on NPU.
Battery temperature observations ranged from 31 to 37 C. No host GPU job,
service termination, firmware/power setting change, or device reboot was used.

One early GPU ten-image trial ended with a system force-stop, not a confirmed
native crash. A GPU fifteen-image trial and a two-image trial were frozen by the
OEM while the secure lockscreen was showing. The user unlocked the phone;
successful boundary trials used a foreground test activity. Interrupted trials
are excluded. The final harness holds and releases a scoped WakeLock, preserves
stage files, and skips ordinary instrumentation runs without an explicit backend.

## Image capacity is conditional

[Gemma 4's model card](https://ai.google.dev/gemma/docs/core/model_card_4) gives
E2B/E4B a 128K-token context, not a universal maximum image count.
[Image understanding documentation](https://ai.google.dev/gemma/docs/capabilities/vision/image)
describes multi-image input and visual budgets of 70, 140, 280, 560, or 1120 tokens;
actual image token counts depend on image geometry. Do not apply Gemini API limits
or the video frame limit as Gemma's maximum number of still images.

The actual SM8750 language model has 4096-token KV tensors and `prefill_128`;
vision exposes `vision_280`, adapter `vision_adapter_280`, and metadata
`max_num_patches=2520` (16x16 patches). It does not expose a fixed image-count
limit in its metadata. The generic bundle has 70/140/280 vision signatures, but
this app uses the metadata's default 2520-patch budget and a 4096-token engine.

Capacity must satisfy:
`text + image framing + sum(actual visual tokens) + output <= context limit`.
The measured fifteen/sixteen boundary is specific to these square fixtures,
default visual budget, short question, empty history, and 4096-token configuration.
Longer questions/history/output reduce capacity; different image geometry or a
different visual budget can change it. Fifteen is not Gemma 4's global maximum.
Keep the app limit at ten until token-aware admission and broader quality checks
are verified.

## Remaining blocker: image-history follow-up

The NPU product route intentionally recreates the Conversation from authoritative
bounded history on each turn while retaining its engine. The benchmark recreated
the USER image turn and MODEL reply exactly as the app does, then asked the color
of the previous image. The engine was reused, but native generation failed with:
`Failed to start nativeSendMessageAsync: INVALID_ARGUMENT: Provided less images than expected in the prompt.`
The first image recognition succeeded (6.645 s on this cached repeat); the
follow-up failed in 0.229 s and is not a valid continuation-speed result.
An earlier harness attempt omitted history and answered "Please provide the image";
that was a benchmark setup error, not proof of an app defect. The corrected
history-restoration failure is the real blocker. CPU/GPU image-history restoration
was not separately executed. Retain Developer access; fix native image-preface
handling or design a bounded image-aware Conversation lifecycle, then re-test
follow-ups, chat reopening, cancellation, and release before promotion.

## Reproduce the manual benchmark

Stage the existing pinned native inputs, not new dependencies. Verify the APK
application ID before installing; never install over the main/TTS APK.

```sh
./gradlew --offline --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process \
  -Plami.imageBenchmark=true -Plami.standardNpuRuntimeEnabled=true \
  :app:assembleStandardDebug :app:assembleStandardDebugAndroidTest

adb -s DEVICE shell am instrument -w -r \
  -e class io.github.ninbyo02.lami.ui.screens.home.LocalImageInferenceBenchmarkInstrumentedTest \
  -e imageBackend GPU -e imageCount 15 -e imageCapacityProbe true -e imagePrompt endpoints \
  io.github.ninbyo02.lami.npuvalidation.imagebench.test/androidx.test.runner.AndroidJUnitRunner
```

Use CPU/GPU/NPU, count 1 or 10 for the product routes; the capacity probe is bounded
to sixteen. The expected sixteen-image context rejection is a passing boundary
check, while its JSON still records failed inference. For the NPU history check,
use count 1 and `-e imageWarmFollowup true`. Results and traces are in app-private
`files/image_benchmark_<BACKEND>_<COUNT>[_capacity].{json,trace}`.
Evidence for this session is saved in the sibling task directory
`gemma4-image-benchmark-evidence`, including raw JSON, traces, structural parsing,
memory snapshots, and excluded/interrupted trials.
