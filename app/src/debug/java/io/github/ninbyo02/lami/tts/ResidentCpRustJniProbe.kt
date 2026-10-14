package io.github.ninbyo02.lami.tts

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Explicit command-line diagnostic; no Activity, manifest registration or audio. */
internal object ResidentCpRustJniProbe {
    @JvmStatic fun main(args: Array<String>) {
        val stdout = PrintStream(FileOutputStream(FileDescriptor.out), true)
        stdout.println("probe_started=true")
        stdout.println("max_heap_bytes=${Runtime.getRuntime().maxMemory()}")
        try { run(args, stdout) } catch (failure: Throwable) {
            stdout.println("status=blocked")
            failure.printStackTrace(PrintStream(FileOutputStream(FileDescriptor.err), true))
            throw failure
        }
    }
    private fun run(args: Array<String>, stdout: PrintStream) {
        require(args.size == 8) { "JNI_LIB RUNTIME CONTEXT SHA256 INPUTS GOLDEN OUTPUT IDENTITY" }
        val library = File(args[0]); val runtime = File(args[1]); val file = File(args[2])
        val spec = ResidentCpContextSpec(args[3], 1024, 16, 32, args[7])
        val output = File(args[6]); require(!output.exists()); check(output.mkdir())
        fun rows(path: String): Array<FloatArray> {
            val bytes = File(path).readBytes(); require(bytes.size == 16 * 1024 * 4)
            val values = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return Array(16) { FloatArray(1024).also { values.get(it); require(it.all(Float::isFinite)) } }
        }
        val inputs = rows(args[4]); val expected = rows(args[5])
        val lines = mutableListOf<String>()
        fun record(key: String, value: Any) { lines += "$key=$value"; stdout.println("$key=$value") }
        var handle = 0L
        ResidentCpRustNativeApi(runtime, spec, library).use { api ->
            val tracing = object : ResidentCpQnnDriver.NativeApi {
                override fun loadContext(asset: String) = api.loadContext(asset).also { handle = it }
                override fun resetKv(context: Long) = api.resetKv(context)
                override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) = api.execute(context, position, input, output)
                override fun release(context: Long) = api.release(context)
            }
            val driver = ResidentCpQnnDriver(tracing, file, spec)
            ResidentCpSession(ResidentCpBufferedBackend(driver, 1024)).use { session ->
                for (phase in listOf("first", "reuse", "after_cancel")) {
                    if (phase == "after_cancel") {
                        session.startFrame(); session.step(inputs[0]); api.cancel(handle); session.cancelFrame()
                    }
                    val begin = System.nanoTime(); session.startFrame()
                    record("${phase}_start_frame_ns", System.nanoTime() - begin)
                    val values = ByteBuffer.allocate(16 * 1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
                    val started = System.nanoTime()
                    repeat(16) { position ->
                        val hidden = session.step(inputs[position])
                        check(hidden.map(Float::toRawBits) == expected[position].map(Float::toRawBits)) { "JNI golden output differs at $phase/$position" }
                        hidden.forEach { values.putFloat(it) }
                    }
                    record("${phase}_step_loop_ns", System.nanoTime() - started)
                    val bytes = values.array(); File(output, "$phase-hidden.f32").writeBytes(bytes)
                    record("${phase}_hidden_sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) })
                    record("${phase}_native_stats", api.stats(handle).joinToString(","))
                }
                val stats = api.stats(handle)
                check(stats[0] == 1L && stats[1] == 49L && stats[3] == 4L && stats[4] == 1L)
                val closeStarted = System.nanoTime(); api.release(handle)
                record("release_ns", System.nanoTime() - closeStarted)
                var rejected = false
                try { driver.forward(0, inputs[0], FloatArray(1024)) } catch (_: IllegalStateException) { rejected = true }
                check(rejected); rejected = false
                try { driver.clearKv() } catch (_: IllegalStateException) { rejected = true }
                check(rejected); record("failed_driver_reuse_rejected", true)
            }
            ResidentCpSession(ResidentCpBufferedBackend(ResidentCpQnnDriver(tracing, file, spec), 1024)).use { fresh ->
                val begin = System.nanoTime(); fresh.startFrame()
                val hidden = fresh.step(inputs[0])
                check(hidden.map(Float::toRawBits) == expected[0].map(Float::toRawBits))
                val stats = api.stats(handle); check(stats[0] == 1L && stats[1] == 1L)
                record("fresh_recovery_ns", System.nanoTime() - begin)
                record("fresh_native_stats", stats.joinToString(","))
                fresh.cancelFrame()
            }
        }
        record("audio_played", false); record("golden_bit_equal", true); record("status", "complete")
        File(output, "result.txt").writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
