# Classification

`1 token生成成功`

- classification: `executed`
- result: `success`
- prompt: `Hi`
- max output tokens: `1`
- output: `!`
- elapsed ms: `907`
- tombstone classification: `stale-tombstone-ignored`

The isolated `customBuildExperimentDebug` lower-level JNI entrypoint executed
once. It did not use the normal UI path, did not create `Conversation`, and
did not call high-level `generateResponse`.
