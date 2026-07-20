#!/usr/bin/env bash
# Adds cancel-handle marker evidence to the debug-only benchmark receiver.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
receiver="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$receiver"
cp -a "$receiver" "$receiver.bak.$timestamp"
RECEIVER="$receiver" python3 - <<'PY'
import os
from pathlib import Path
p=Path(os.environ['RECEIVER']); t=p.read_text()
def once(old,new,tag):
 global t
 n=t.count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 t=t.replace(old,new)
once('''        activeCaseFuture.set(future)
        return try {
''','''        activeCaseFuture.set(future)
        val cancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail, maxOutputTokensList)
        }
        activeCancelMarker.set(cancelMarker)
        return try {
''','cancel marker registration')
once('''        } catch (_: CancellationException) {
            LiteRtLmGpuBenchmarkRow.failure(
''','''        } catch (_: CancellationException) {
            activeCancelMarker.get()?.invoke(
                "case_cancelled",
                "reason=cancelled_by_debug_foreground_ui future_cancelled=true",
            )
            LiteRtLmGpuBenchmarkRow.failure(
''','case cancelled marker')
once('''        } finally {
            activeCaseFuture.compareAndSet(future, null)
            executor.shutdownNow()
''','''        } finally {
            activeCaseFuture.compareAndSet(future, null)
            activeCancelMarker.compareAndSet(cancelMarker, null)
            executor.shutdownNow()
''','cancel marker release')
once('''        private val activeEngine = AtomicReference<Engine?>(null)
        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
''','''        private val activeEngine = AtomicReference<Engine?>(null)
        private val activeCancelMarker = AtomicReference<((String, String) -> Unit)?>(null)
        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
''','active marker field')
once('''        fun cancelCurrentRun() {
            cancelRequested.set(true)
            activeCaseFuture.getAndSet(null)?.cancel(true)
            val conversation = activeConversation.getAndSet(null)
            val engine = activeEngine.getAndSet(null)
            if (conversation != null || engine != null) {
                cancelCloseDispatcher.execute {
                    runCatching { conversation?.close() }
                    runCatching { engine?.close() }
                }
            }
        }
''','''        fun cancelCurrentRun() {
            cancelRequested.set(true)
            val marker = activeCancelMarker.get()
            val future = activeCaseFuture.getAndSet(null)
            val cancelAccepted = future?.cancel(true) ?: false
            val conversation = activeConversation.getAndSet(null)
            val engine = activeEngine.getAndSet(null)
            marker?.invoke(
                "cancel_future_requested",
                "future_present=${future != null} cancel_accepted=$cancelAccepted conversation_present=${conversation != null} engine_present=${engine != null}",
            )
            if (conversation != null || engine != null) {
                cancelCloseDispatcher.execute {
                    marker?.invoke("cancel_close_started", "conversation_present=${conversation != null} engine_present=${engine != null}")
                    runCatching { conversation?.close() }
                    runCatching { engine?.close() }
                    marker?.invoke("cancel_close_finished", "conversation_present=${conversation != null} engine_present=${engine != null}")
                }
            }
        }
''','cancel marker evidence')
p.write_text(t)
PY
printf 'debug_token_ui_cancel_marker_diagnostic=enabled\nbackup=%s\n' "$receiver.bak.$timestamp"
