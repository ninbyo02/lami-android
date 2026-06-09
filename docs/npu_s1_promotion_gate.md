# NPU S1 Promotion Gate

## Purpose

This document records the safety gate required before restoring the custom JNI
NPU S1 route to normal chat. The current implementation intentionally keeps
normal chat blocked:

```text
reason=npu_s1_native_route_blocked_for_normal_chat
```

The gate added here is diagnostic and policy-oriented. It does not reconnect
normal chat to `nativeRunEditablePrompt`.

## Current Observation

After rebuilding with the explicit QAIRT 2.44 overlay:

```text
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

the Qualcomm companion libraries match the known-good build:

```text
libLiteRtDispatch_Qualcomm.so
  sha256=7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f
  build_id=283f860170c8b970f14db885eab73a95

libLiteRtCompilerPlugin_Qualcomm.so
  sha256=c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c
  build_id=443391d4c4348191230b67a3ab8a6037
```

DEV probe results:

```text
editable_engine_create_only_minimal=success
editable_engine_create_only=success
engine_create_only=success
full_20=success
run_count_completed=20
success_count=20
failure_count=0
engine_create_count=1
decode_attempt_count=20
decode_success_count=20
engine_close_reached=true
engine_close_success=true
backend_evidence=QNN_HTP_V79_FastRPC_native_diag_persistent_holder
persistent_custom_jni_hypothesis_result=engine_create_once_20_runs_success
```

This strongly points to the previous `SIGABRT` being caused by
Dispatch / CompilerPlugin / QAIRT overlay mismatch rather than the persistent
holder, mutex, session, prefill, decode, or full loop.

## Promotion Gate

The crash-safety gate passes only when the DEV persistent custom JNI full_20
summary satisfies:

- `selected_native_probe_mode=full_20`
- `persistent_custom_jni_status=completed`
- `backend_evidence` contains `QNN_HTP_V79`
- `success_count == run_count_requested`
- `failure_count == 0`
- `decode_attempt_count == run_count_requested`
- `decode_success_count == run_count_requested`
- `engine_create_count == 1`
- `engine_close_reached=true`
- `engine_close_success=true`
- `fresh_crash=false`
- `timeout=false`
- `fallback=false`

The gate also emits a manual tombstone comparison hint:

```text
npu_s1_promotion_gate_tombstone_manual_check=required
npu_s1_promotion_gate_tombstone_compare_hint=manually_compare_probe_wall_time_with_dumpsys_dropbox_and_data_tombstones
```

Passing this gate means the Engine create / Session / Prefill / Decode crash
safety hypothesis is currently satisfied. It does not mean the route is ready
for normal chat.

## Normal Chat Policy

Normal chat remains blocked by policy:

```text
npu_s1_promotion_gate_normal_chat_unblock_allowed=false
npu_s1_promotion_gate_normal_chat_unblock=blocked_by_policy
```

The user-facing message is separated from the DEV reason. Normal chat should
show:

```text
NPU推論は安全確認中のため、通常チャットでは一時的に無効化されています。
```

DEV diagnostics still keep:

```text
reason=npu_s1_native_route_blocked_for_normal_chat
normal_chat_native_route_blocked=true
```

## Remaining Risk

The current full_20 result is a crash-safety pass, not an output-quality pass.
Observed raw output can still look like template drift:

```text
。お元気ですか。いつもお世話になっております...
```

Before normal chat can use this route, a separate output-quality gate is
required. The next work should focus on:

- prompt template alignment
- input/context limit behavior
- output stop conditions
- natural Japanese quality classification
- repeated normal-chat prompts with tombstone/dropbox comparison

## Next Device Check

1. Run DEV diagnostics.
2. Execute `NPU S1 persistent custom JNI` with `full_20`.
3. Confirm:
   - `npu_s1_promotion_gate_status=pass`
   - `npu_s1_promotion_gate_crash_safety=pass`
   - `npu_s1_promotion_gate_output_quality=suspect`
   - `npu_s1_promotion_gate_normal_chat_unblock=blocked_by_policy`
4. Manually compare the run time with dropbox/tombstone timestamps.
5. Do not restore normal chat NPU routing until the output-quality gate is
   designed and passed.

