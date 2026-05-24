# Classification

`SetMaxOutputTokens(1)経路なし -> 未実行`

LiteRT-LM C++ has the needed lower-level decode cap, but the lami
`customBuildExperimentDebug` app does not yet expose a runnable isolated JNI/CLI
entrypoint that calls `DecodeConfig.SetMaxOutputTokens(1)`.
