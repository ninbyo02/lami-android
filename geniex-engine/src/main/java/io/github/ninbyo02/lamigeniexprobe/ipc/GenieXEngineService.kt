package io.github.ninbyo02.lamigeniexprobe.ipc

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import io.github.ninbyo02.lamigeniexprobe.ComputeTarget
import io.github.ninbyo02.lamigeniexprobe.GenieXResidentSession
import io.github.ninbyo02.lamigeniexprobe.Lfm3ModelPolicy
import io.github.ninbyo02.lamigeniexprobe.integration.LamiInferenceRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

class GenieXEngineService : Service() {
    private class Request(val id: Int, val uid: Int) {
        val stop = CompletableDeferred<Unit>()
        lateinit var job: Job
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resident by lazy { GenieXResidentSession(applicationContext) }
    private val requests = Any()
    private var active: Request? = null
    private var destroyed = false

    private fun enforceBoundCaller() {
        enforceCallingPermission(
            "io.github.ninbyo02.lamigeniexprobe.permission.BIND_ENGINE",
            "Caller must hold the GenieX engine signature permission",
        )
    }

    private fun parseTurns(raw: String): List<GenieXResidentSession.Turn> {
        require(raw.length in 2..LamiInferenceRequest.MAX_WIRE_CHARS)
        val array = JSONArray(raw)
        require(array.length() in 1..LamiInferenceRequest.MAX_TURNS)
        val turns = (0 until array.length()).map { index ->
            val turn = array.getJSONObject(index)
            GenieXResidentSession.Turn(turn.getString("role"), turn.getString("content"))
        }
        require(turns.sumOf { it.text.length } <= LamiInferenceRequest.MAX_HISTORY_CHARS)
        require(turns.last().role == "user")
        return turns
    }

    private fun sendError(callback: IGenieXCallback, id: Int, code: String, message: String) {
        runCatching { callback.onError(id, code, message) }
    }

    private suspend fun runRequest(
        request: Request,
        target: ComputeTarget,
        turns: List<GenieXResidentSession.Turn>,
        maxTokens: Int,
        callback: IGenieXCallback,
    ) {
        val caller = callback.asBinder()
        val death = IBinder.DeathRecipient { request.stop.complete(Unit) }
        val result = try {
            caller.linkToDeath(death, 0)
            val model = Lfm3ModelPolicy.findModel(this) ?: throw MissingModelException()
            Result.success(resident.generate(model, target, turns, maxTokens, request.stop) { chunk ->
                callback.onToken(request.id, chunk)
            }.toMetricsJson().toString())
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            runCatching { caller.unlinkToDeath(death, 0) }
            synchronized(requests) {
                if (active === request) active = null
            }
        }
        result.fold(
            onSuccess = { metrics -> runCatching { callback.onComplete(request.id, metrics) } },
            onFailure = { error ->
                when (error) {
                    is CancellationException ->
                        sendError(callback, request.id, "CANCELLED", "GenieX generation was stopped.")
                    is RemoteException -> Unit
                    is MissingModelException ->
                        sendError(callback, request.id, "MODEL_MISSING", "Import the LFM3 GGUF in LAMI GenieX Engine.")
                    else -> sendError(callback, request.id, "ENGINE_ERROR", error.javaClass.simpleName)
                }
            },
        )
    }

    private class MissingModelException : Exception()

    private val binder = object : IGenieXEngine.Stub() {
        override fun getProtocolVersion(): Int {
            enforceBoundCaller()
            return LamiInferenceRequest.PROTOCOL_VERSION
        }

        override fun isModelReady(): Boolean {
            enforceBoundCaller()
            return Lfm3ModelPolicy.findModel(this@GenieXEngineService) != null
        }

        override fun startGeneration(
            requestId: Int,
            compute: String,
            turnsJson: String,
            maxTokens: Int,
            callback: IGenieXCallback,
        ) {
            enforceBoundCaller()
            val target: ComputeTarget
            val turns: List<GenieXResidentSession.Turn>
            try {
                require(requestId >= 0 && maxTokens in 1..LamiInferenceRequest.MAX_OUTPUT_TOKENS)
                target = ComputeTarget.entries.single { it.value == compute }
                turns = parseTurns(turnsJson)
            } catch (error: Exception) {
                sendError(callback, requestId, "BAD_REQUEST", "Invalid GenieX generation request.")
                return
            }

            val request = Request(requestId, Binder.getCallingUid())
            val job = synchronized(requests) {
                if (active != null || destroyed) return@synchronized null
                scope.launch(start = CoroutineStart.LAZY) {
                    runRequest(request, target, turns, maxTokens, callback)
                }.also {
                    request.job = it
                    active = request
                }
            }
            if (job == null) {
                sendError(callback, requestId, "BUSY", "GenieX is finishing another request.")
            } else {
                job.start()
            }
        }

        override fun stop(requestId: Int) {
            enforceBoundCaller()
            val uid = Binder.getCallingUid()
            val request = synchronized(requests) {
                active?.takeIf { it.id == requestId && it.uid == uid }
            }
            request?.stop?.complete(Unit)
        }

        override fun releaseEngine() {
            enforceBoundCaller()
            val idle = synchronized(requests) { active == null && !destroyed }
            if (idle) scope.launch { resident.close() }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        val request = synchronized(requests) {
            destroyed = true
            active
        }
        request?.stop?.complete(Unit)
        scope.launch {
            try {
                request?.job?.join()
                resident.close()
            } finally {
                scope.cancel()
            }
        }
        super.onDestroy()
    }
}
