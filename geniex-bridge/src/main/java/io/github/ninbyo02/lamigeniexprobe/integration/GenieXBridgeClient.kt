package io.github.ninbyo02.lamigeniexprobe.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.ninbyo02.lamigeniexprobe.ipc.IGenieXCallback
import io.github.ninbyo02.lamigeniexprobe.ipc.IGenieXEngine
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native-free, persistent Binder client for the separate GenieX service.
 *
 * Keep ONE instance alive for a LAMI conversation (e.g. in the inference ViewModel).
 * A stream ending does NOT unbind the service, so its resident model remains loaded.
 * Call close() when the owner is disposed or switching away from GenieX.
 */
class GenieXBridgeClient(context: Context) : LamiTextEngine, Closeable {
    private val app = context.applicationContext
    private val connectionMutex = Mutex()
    private val activeFlows = ConcurrentHashMap<Int, SendChannel<LamiEngineEvent>>()
    @Volatile private var engine: IGenieXEngine? = null
    @Volatile private var pending: CompletableDeferred<IGenieXEngine>? = null
    @Volatile private var bound = false
    private val closed = AtomicBoolean(false)

    private val intent = Intent().setClassName(
        "io.github.ninbyo02.lamigeniexprobe",
        "io.github.ninbyo02.lamigeniexprobe.ipc.GenieXEngineService",
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val proxy = IGenieXEngine.Stub.asInterface(binder)
            if (proxy == null) {
                pending?.completeExceptionally(IllegalStateException("Null engine binder"))
                return
            }
            if (closed.get()) return
            engine = proxy
            pending?.complete(proxy)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engine = null
            pending?.completeExceptionally(IllegalStateException("GenieX service lost"))
            activeFlows.values.forEach { channel ->
                channel.trySend(LamiEngineEvent.Failure(
                    "DISCONNECTED", "GenieX service disconnected during generation"
                ))
                channel.close()
            }
            activeFlows.clear()
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)

        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }

    private suspend fun connect(): IGenieXEngine = connectionMutex.withLock {
        check(!closed.get()) { "GenieX client has been closed" }
        engine?.let { return@withLock it }
        // A previously disconnected binder can retain a stale binding.
        // Rebind to force a fresh connection when there is no live proxy.
        if (bound) {
            runCatching { app.unbindService(connection) }
            bound = false
        }
        val signal = CompletableDeferred<IGenieXEngine>()
        pending = signal
        try {
            if (!bound) {
                val okay = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                check(okay) { "GenieX service is not installed or permission is denied" }
                bound = true
            }
            check(!closed.get()) { "GenieX client has been closed" }
            val proxy = withTimeout(10_000L) { signal.await() }
            check(proxy.protocolVersion == LamiInferenceRequest.PROTOCOL_VERSION) {
                "Update the LAMI GenieX Engine companion"
            }
            return@withLock proxy
        } catch (error: Throwable) {
            if (bound) runCatching { app.unbindService(connection) }
            bound = false
            engine = null
            throw error
        } finally {
            pending = null
        }
    }

    override fun stream(request: LamiInferenceRequest): Flow<LamiEngineEvent> = callbackFlow {
        val proxy = try {
            connect()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            trySend(LamiEngineEvent.Failure("BIND_FAILED", error.message ?: "GenieX is unavailable"))
            close()
            return@callbackFlow
        }
        val completed = AtomicBoolean(false)
        val protocolFailed = AtomicBoolean(false)
        val chunks = AtomicInteger(0)
        val history = JSONArray().apply {
            request.turns.forEach { turn ->
                put(JSONObject().apply {
                    put("role", turn.role)
                    put("content", turn.content)
                })
            }
        }.toString()

        val listener = object : IGenieXCallback.Stub() {
            override fun onToken(requestId: Int, text: String) {
                if (requestId == request.id && !completed.get()) {
                    if (text.length > 2048 || chunks.incrementAndGet() > 1024) {
                        if (completed.compareAndSet(false, true)) {
                            protocolFailed.set(true)
                            trySend(LamiEngineEvent.Failure(
                                "STREAM_LIMIT", "GenieX exceeded the stream limit"
                            ))
                            close()
                        }
                    } else {
                        trySend(LamiEngineEvent.Token(text))
                    }
                }
            }

            override fun onComplete(requestId: Int, metricsJson: String) {
                if (requestId == request.id && completed.compareAndSet(false, true)) {
                    trySend(LamiEngineEvent.Complete(metricsJson))
                    close()
                }
            }

            override fun onError(requestId: Int, code: String, message: String) {
                if (requestId == request.id && completed.compareAndSet(false, true)) {
                    trySend(LamiEngineEvent.Failure(code, message.take(200)))
                    close()
                }
            }
        }

        if (activeFlows.putIfAbsent(request.id, channel) != null) {
            trySend(LamiEngineEvent.Failure("DUPLICATE_ID", "Request id already in use"))
            close()
            return@callbackFlow
        }
        try {
            proxy.startGeneration(
                request.id, request.compute.wireName, history, request.maxTokens, listener
            )
        } catch (error: Exception) {
            if (completed.compareAndSet(false, true)) {
                trySend(LamiEngineEvent.Failure("IPC_START_FAILED", error.javaClass.simpleName))
                close()
            }
        }

        awaitClose {
            activeFlows.remove(request.id, channel)
            if (!completed.get() || protocolFailed.get()) runCatching { proxy.stop(request.id) }
            // Deliberately do NOT unbind the service here: keep the model resident.
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO) // The callback checks the maximum chunk count.

    /** Checks that the companion has the model without loading weights. */
    suspend fun checkModelReady(): Boolean = withContext(Dispatchers.IO) {
        connect().isModelReady
    }

    /** Closes native weights only when the service has no active request. */
    suspend fun releaseModel() = withContext(Dispatchers.IO) {
        connect().releaseEngine()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val proxy = engine
        activeFlows.forEach { (id, channel) ->
            runCatching { proxy?.stop(id) }
            channel.trySend(LamiEngineEvent.Failure("CLIENT_CLOSED", "Client disposed"))
            channel.close()
        }
        activeFlows.clear()
        engine = null
        pending?.completeExceptionally(IllegalStateException("Client was closed"))
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
    }
}
