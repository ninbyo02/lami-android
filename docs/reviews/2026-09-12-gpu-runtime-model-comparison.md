# GPU runtime/model comparison — 2026-09-12

## Trigger and verified failure

The user's StandardDebug UI run at 18:34 JST selected GPU, NPU route OFF, generic model size 2,583,085,056 bytes. Engine initialization and conversation creation succeeded. The final official-flow attempt failed with LiteRtLmJniException status 13 at llm_litert_compiled_model_executor.cc:735 (compiled-model invoke). The diagnostic nativeMarker UnsatisfiedLinkError is caught during diagnostic collection, not the generation exception. Displayed 0.3 token/s is not usable GPU throughput: failure-message text enters estimated output metrics. The fallback flag is also derived from an internal API fallback reason, not proof of CPU backend execution.

## Artifact comparison

The current StandardDebug APK is PR #2597's verified installed artifact (SHA 208067c6d409c84fa08b81b2996405278c1cdf4e5b7475bfab76541f4dbd88ad). The separately installed gpustandardminimal APK has a different libLiteRt and liblitertlm_jni pair. Both JNI libraries declare the same direct shared-library names, which does not establish ABI or runtime equivalence. In particular the installed minimal JNI hash is 310e37ff..., not the ac97fd1a... hash named in the historical staging script. Package name or old staging instructions are insufficient provenance.

## Fresh physical-device checks

Device NX733J. Prompt: こんにちは. Diagnostic receiver, isolated fresh process per run. Native libraries and user backend/model preferences were not replaced. The current standard model was copied into the minimal package under a new diagnostic filename. The initial PC-mediated copy was stopped and replaced by an on-device copy.

| APK | Model bytes | Variant / actual context | Result |
| --- | ---: | --- | --- |
| Installed minimal | 2,588,147,712 | gallery-chat-parity / 4096 | Completed; 12 callbacks, onDone=1, onError=0 |
| Installed minimal | 2,583,085,056 (current standard model copy) | gallery-chat-parity / 4096 | Completed; 12 callbacks, onDone=1, onError=0 |
| Installed minimal | 2,583,085,056 | gpu-null-modalities / 1024 | No final report by 70-second host observation deadline; last marker engine_create_started; force-stopped |
| Current standard | 2,583,085,056 | gpu-null-modalities / 1024 | No final report by 70-second host observation deadline; last marker engine_create_started; force-stopped |

Both completed replies were natural Japanese greetings. These are one short run per condition, not long-output stability measurements. Native callback count is not a measured token count.

The installed minimal receiver's actual parity context was 4096, despite a passed legacy max_output_tokens value of 1024. Current repository source uses a different parity constant; actual device markers govern this report. The 1024 variant also changes cache (app cache directory rather than null), sampler and send-path policy relative to parity. Thus these results do NOT isolate context capacity or native libraries alone. Differences between APK receiver versions remain a confounder.

One initial old-model run was invalid: adb exec-out returned a missing-file message with exit status zero, causing the host to stop before completion. That run was excluded and repeated with report-content validation. The two 1024 results are host-deadline observations, not completed runtime timeout reports; the requested 45-second receiver timeout did not yield a terminal artifact before host termination.

## Conclusion and next discriminator

The current model can generate on this phone's GPU in the isolated minimal configuration. Model corruption or an inherently GPU-incompatible model is therefore less likely. NPU-ready StandardDebug and the successful GPU candidate are not yet a jointly qualified runtime configuration. A simple native-library replacement would risk the working NPU path and is not justified.

Next: build isolated diagnostic packages from one exact app/receiver revision, preserve each coherent native stack, explicitly set the same context, null cache, modalities, sampler and send API, and record resolved values. Compare current model on both. Only then vary context or cache individually. Do not claim 1024 is the cause, or raise production context based on this comparison.

The misleading failure throughput and backend-fallback labels should be corrected separately from runtime behavior. No application code was changed in this investigation. Both diagnostic processes were stopped and ChatGPT returned to foreground. Existing app preferences, models and chat history were not edited; only additional diagnostic files/cache were created.
