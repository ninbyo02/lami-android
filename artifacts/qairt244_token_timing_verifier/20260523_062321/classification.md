# Classification

`1 token生成成功`

- classification: `executed`
- result: `success`
- prompt: `Hi`
- max output tokens: `1`
- output: `!`
- prompt bytes: `2`
- prompt token count: `unavailable`
- output bytes: `1`
- output token count: `unavailable`
- elapsed ms: `1053`
- decode elapsed ms: `22`
- tombstone classification: `stale-tombstone-ignored`

The isolated `customBuildExperimentDebug` lower-level JNI entrypoint executed
once. It did not use the normal UI path, did not create `Conversation`, and
did not call high-level `generateResponse`.
