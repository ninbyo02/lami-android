# Device create stage isolated on SM8750 (2026-10-14 JST)

Isolated diagnostic APK on NX733J Android 16. Model SHA-256 pinned to production-fp32.
Rust and C bridge encode the first failing QNN-open stage as JNI status 101..108.
The single rerun returned 105: QNN deviceCreate stage (fifth stage), after RPC-loader,
provider lookup, graph metadata and backend creation. Context creation and graph execution
were not reached. This is not proof of native acceleration or speech quality.

The current diagnostic retains failure stage, not the complete QNN SDK error code.
Next inspect SDK deviceCreate requirements, SoC/runtime compatibility, and capture the
underlying SDK status before another physical device attempt. Do not change OS vendor
libraries, mix QAIRT versions, or claim successful inference.
