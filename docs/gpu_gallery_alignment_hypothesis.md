# GPU Gallery Alignment Hypothesis

## Scope

This compares the debug-only Lami GPU benchmark receiver with local Google AI Edge Gallery and LiteRT-LM Kotlin sources.

No production code was changed.

## Gallery Version Signals

Local Gallery Android source uses:

```text
com.google.ai.edge.litertlm:litertlm-android:0.11.0
```

Gemma 4 E2B appears in Gallery allowlists:

| allowlist | model file | size | default maxTokens | accelerators | note |
| --- | --- | ---: | ---: | --- | --- |
| `1_0_12` | `gemma-4-E2B-it.litertlm` | `2583085056` | `4000` | `gpu,cpu` | Matches current Lami model length. |
| `1_0_15` | `gemma-4-E2B-it.litertlm` | `2588147712` | `4000` | `gpu,cpu` | Has updatable file metadata and MTP update note. |

Current Lami benchmark model length is `2583085056`, so it aligns with Gallery `1_0_12` more closely than `1_0_15`.

## Gallery Chat Setup

Gallery chat initializes through `LlmChatModelHelper.initialize(...)`.

For `llm_chat`, `LlmChatTask.initializeModelFn(...)` passes:

```text
supportImage=false
supportAudio=false
```

Therefore Gallery chat builds:

| field | Gallery chat value |
| --- | --- |
| `backend` | selected accelerator, default GPU for Gemma 4 E2B |
| `visionBackend` | `null` for text chat |
| `audioBackend` | `null` for text chat |
| `maxNumTokens` | model config `maxTokens`, `4000` for Gemma 4 E2B |
| `cacheDir` | `null` for normal app-private model path; external files dir only for `/data/local/tmp` |

Then it creates:

```kotlin
ConversationConfig(
  samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
  systemInstruction = systemInstruction,
  tools = tools,
)
```

For GPU/CPU, sampler config is non-null. For NPU/TPU, Gallery leaves sampler config null.

## Gallery Send Setup

Gallery builds a content list and sends through the callback overload:

```kotlin
val contents = mutableListOf<Content>()
contents.add(Content.Text(input))

conversation.sendMessageAsync(
  Contents.of(contents),
  object : MessageCallback { ... },
  extraContext ?: emptyMap(),
)
```

For normal text chat there are no image/audio contents, but the text is still sent as `Contents.of(listOf(Content.Text(input)))`.

## Receiver Differences

| area | current receiver | Gallery chat | priority |
| --- | --- | --- | --- |
| modality backend | variant-dependent; some use `visionBackend=GPU`, `audioBackend=CPU`; `gpu-null-modalities` uses null/null | null/null for text chat | high |
| cache dir | app cache dir path | null for app-private model path | medium |
| max token | often `32/64/128`, or null in default/null-max variants | `4000` for Gemma 4 E2B | high |
| conversation setup | no-arg `engine.createConversation()` | `ConversationConfig` with sampler | high |
| sampler | native/model default | topK 64, topP 0.95, temperature 1.0 | medium-high |
| prompt formatting | `sendMessageAsync(prompt)` then `sendMessage(prompt)` fallback | `sendMessageAsync(Contents.of(listOf(Content.Text(input))), callback, extraContext)` | high |
| system instruction | none | task default/custom system prompt can be present | medium |
| extra context | empty by default | empty unless thinking is enabled | low for non-thinking probe |
| streaming shape | Kotlin Flow wrapper first | callback overload | medium |
| lifecycle | fresh Engine/Conversation per prompt | held `LlmModelInstance` with one Engine and resettable Conversation | medium |

## Hypothesis

The minimal Gallery-aligned receiver config should be:

```text
backend=Backend.GPU()
visionBackend=null
audioBackend=null
maxNumTokens=4000
cacheDir=null
ConversationConfig.samplerConfig=SamplerConfig(64, 0.95, 1.0)
send API=sendMessageAsync(Contents.of(Content.Text(prompt)), MessageCallback, emptyMap())
```

This is not a production recommendation. It is a debug-only probe shape to determine whether the current `nativeSendMessage` failure is caused by receiver-specific configuration.

## Non-Goals

Do not use this investigation to change:

- production ChatScreen behavior
- NPU Standard Route S1-S5
- `Backend.NPU` wiring
- QAIRT/QNN library staging
- fallback policy
