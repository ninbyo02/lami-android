# Classification

`1 token生成成功`

The isolated `customBuildExperimentDebug` lower-level JNI entrypoint executed
once with prompt `Hi` and a hard `DecodeConfig.SetMaxOutputTokens(1)` cap.

Result file:

```text
result=success
max_output_tokens=1
output=!
elapsed_ms=1115
```

The run did not use the normal UI path, did not create `Conversation`, and did
not call high-level `generateResponse`.
