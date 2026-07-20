#!/usr/bin/env bash
# Implements cooperative frontend Stop for the debug-only LiteRT benchmark receiver.
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
p = Path(os.environ['RECEIVER'])
t = p.read_text()

def once(old, new, tag):
    global t
    n = t.count(old)
    if n != 1:
        raise SystemExit(f'{tag} anchor count={n}')
    t = t.replace(old, new)

once('import java.util.concurrent.Executors\n', 'import java.util.concurrent.Executors\nimport java.util.concurrent.Future\n', 'Future import')
once('''        )
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
''', '''        )
        activeCaseFuture.set(future)
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
''', 'active future registration')
once('''        } catch (throwable: Throwable) {
            LiteRtLmGpuBenchmarkRow.failure(
''', '''        } catch (_: CancellationException) {
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = "cancelled_by_debug_foreground_ui",
                modelExists = true,
                modelLength = modelLength,
                timeout = false,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        } catch (throwable: Throwable) {
            LiteRtLmGpuBenchmarkRow.failure(
''', 'cancellation terminal row')
once('''        } finally {
            executor.shutdownNow()
        }
    }

    private fun runCase(
''', '''        } finally {
            activeCaseFuture.compareAndSet(future, null)
            executor.shutdownNow()
        }
    }

    private fun runCase(
''', 'active future release')
once('''                engine = Engine(config)
                engine.initialize()
                engineCreateMs = SystemClock.elapsedRealtime() - engineStartMs
''', '''                engine = Engine(config)
                engine.initialize()
                activeEngine.set(engine)
                if (cancelRequested.get()) throw CancellationException("cancelled_by_debug_foreground_ui")
                engineCreateMs = SystemClock.elapsedRealtime() - engineStartMs
''', 'active engine registration')
once('''            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStartMs
            writeMarker(
''', '''            activeConversation.set(conversation)
            if (cancelRequested.get()) throw CancellationException("cancelled_by_debug_foreground_ui")
            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStartMs
            writeMarker(
''', 'active conversation registration')
once('''                conversation = conversation,
                engine = engine,
            )
''', '''                conversation = claimActiveConversation(conversation),
                engine = claimActiveEngine(engine),
            )
''', 'single close ownership')
once('''        private val running = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)

        /** Cooperative cancellation used only by the debug foreground UI. */
        fun cancelCurrentRun() {
            cancelRequested.set(true)
        }
        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
''', '''        private val running = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val activeCaseFuture = AtomicReference<Future<*>?>(null)
        private val activeConversation = AtomicReference<Conversation?>(null)
        private val activeEngine = AtomicReference<Engine?>(null)
        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }

        /** Cooperative cancellation used only by the debug foreground UI. */
        fun cancelCurrentRun() {
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

        private fun claimActiveConversation(expected: Conversation?): Conversation? =
            if (expected != null && activeConversation.compareAndSet(expected, null)) expected else null

        private fun claimActiveEngine(expected: Engine?): Engine? =
            if (expected != null && activeEngine.compareAndSet(expected, null)) expected else null

        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
''', 'shared cancellation handles')
p.write_text(t)
PY
printf 'debug_token_ui_stop_green=enabled\nbackup=%s\n' "$receiver.bak.$timestamp"
