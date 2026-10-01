package io.github.ninbyo02.lami.tts

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Debug-only file transport avoids Binder's transaction-size limit for long PCM. */
internal object LamiVoiceDecoderProcess {
    const val DECODE = 1
    const val SUCCESS = 2
    const val FAILURE = 3
    const val FORWARD_STARTED = 4

    suspend fun decode(context: Context, root: File, codes: LamiVoiceCodes, progress: (String) -> Unit): FloatArray {
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val directory = File(app.cacheDir, "voice-decoder/$id").apply { check(mkdirs()) }
        val result = CompletableDeferred<Bundle>()
        var bound = false
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getString("id") == id) {
                if (message.what == FORWARD_STARTED) progress("stage=pcm_process_forward pid=${message.data.getInt("pid")}")
                else if (message.what == SUCCESS) result.complete(message.data)
                else result.completeExceptionally(IllegalStateException(message.data.getString("error")))
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    Messenger(binder).send(Message.obtain(null, DECODE).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString("id", id)
                            putString("model", root.resolve("speech-decoder-dynamic-et14.pte").canonicalPath)
                        }
                    })
                } catch (error: Exception) { result.completeExceptionally(error) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                result.completeExceptionally(IllegalStateException("Voice decoder process disconnected"))
            }
            override fun onBindingDied(name: ComponentName) {
                result.completeExceptionally(IllegalStateException("Voice decoder binding died"))
            }
            override fun onNullBinding(name: ComponentName) {
                result.completeExceptionally(IllegalStateException("Voice decoder returned no binder"))
            }
        }
        try {
            require(codes.frames in 2..256 && codes.values.size == codes.frames * 16)
            DataOutputStream(directory.resolve("codes.bin").outputStream().buffered()).use { output ->
                output.writeInt(codes.frames)
                codes.values.forEach { output.writeLong(it) }
            }
            val started = SystemClock.elapsedRealtime()
            bound = app.bindService(Intent(app, LamiVoiceDecoderService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Unable to bind voice decoder" }
            val metrics = withTimeout(60_000L) { result.await() }
            val pid = metrics.getInt("pid")
            check(pid > 0 && pid != Process.myPid()) { "Decoder did not run in separate process" }
            progress("metric=decoder_process pid=$pid model_load_ms=${metrics.getLong("load_ms")} pcm_forward_ms=${metrics.getLong("forward_ms")} total_ms=${SystemClock.elapsedRealtime() - started}")
            return DataInputStream(directory.resolve("pcm.bin").inputStream().buffered()).use { input ->
                require(input.readInt() == codes.frames * 1920) { "Unexpected decoder sample count" }
                FloatArray(codes.frames * 1920) { input.readFloat() }.also {
                    require(input.read() == -1) { "Unexpected trailing decoder output" }
                    LamiPcmContract.validate(it)
                }
            }
        } finally {
            result.cancel()
            if (bound) app.unbindService(connection)
            directory.deleteRecursively()
        }
    }
}

/** One fresh native runtime per bind; unbinding also terminates a blocked native forward. */
class LamiVoiceDecoderService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("lami-pcm-decoder").apply { start() }
        messenger = Messenger(Handler(worker.looper) { message ->
            if (message.what == LamiVoiceDecoderProcess.DECODE) decode(message)
            true
        })
    }
    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onUnbind(intent: Intent?): Boolean {
        // This service is declared in :voice_decoder and never shares the UI process.
        if (android.app.Application.getProcessName() == "$packageName:voice_decoder") {
            Process.killProcess(Process.myPid())
        }
        return false
    }
    private fun decode(message: Message) {
        val reply = message.replyTo ?: return
        val id = message.data.getString("id") ?: return
        val response = Bundle().apply { putString("id", id) }
        var status = LamiVoiceDecoderProcess.FAILURE
        try {
            require(id.matches(Regex("[0-9a-f-]{36}")))
            val directory = File(cacheDir, "voice-decoder/$id")
            val model = File(requireNotNull(message.data.getString("model"))).canonicalFile
            require(model.path.startsWith(filesDir.canonicalPath + File.separator))
            val (frames, codes) = DataInputStream(directory.resolve("codes.bin").inputStream().buffered()).use { input ->
                val count = input.readInt()
                require(count in 2..256)
                val values = LongArray(count * 16) { input.readLong().also { require(it in 0..2047) } }
                require(input.read() == -1)
                count to values
            }
            val started = SystemClock.elapsedRealtime()
            Module.load(model.path, Module.LOAD_MODE_MMAP).use { decoder ->
                decoder.loadMethod("forward")
                response.putLong("load_ms", SystemClock.elapsedRealtime() - started)
                response.putInt("pid", Process.myPid())
                reply.send(Message.obtain(null, LamiVoiceDecoderProcess.FORWARD_STARTED).apply { data = Bundle(response) })
                val forwardStarted = SystemClock.elapsedRealtime()
                val pcm = decoder.forward(EValue.from(Tensor.fromBlob(codes, longArrayOf(1, 16, frames.toLong()))))
                    .single().toTensor().dataAsFloatArray
                response.putLong("forward_ms", SystemClock.elapsedRealtime() - forwardStarted)
                require(pcm.size == frames * 1920)
                LamiPcmContract.validate(pcm)
                DataOutputStream(directory.resolve("pcm.bin").outputStream().buffered()).use { output ->
                    output.writeInt(pcm.size)
                    pcm.forEach(output::writeFloat)
                }
            }
            response.putInt("pid", Process.myPid())
            status = LamiVoiceDecoderProcess.SUCCESS
        } catch (error: Throwable) {
            response.putString("error", "${error.javaClass.simpleName}: ${error.message}")
        }
        runCatching { reply.send(Message.obtain(null, status).apply { data = response }) }
    }
}
