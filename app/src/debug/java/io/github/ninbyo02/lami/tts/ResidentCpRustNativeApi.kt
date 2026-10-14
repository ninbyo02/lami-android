package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest

/** Debug-only opt-in adapter. It is not registered with ordinary TTS routing. */
internal class ResidentCpRustNativeApi(
    private val runtime: File,
    private val spec: ResidentCpContextSpec,
    private val library: File? = null,
    private val bridge: Bridge = ResidentCpRustJni,
) : ResidentCpQnnDriver.NativeApi, AutoCloseable {
    internal interface Bridge {
        fun loadLibrary(file: File?)
        fun prepareRuntime(runtime: File)
        fun create(bytes: ByteArray, backend: String, system: String, rpc: String): Long
        fun reset(handle: Long)
        fun execute(handle: Long, position: Int, input: FloatArray, output: FloatArray)
        fun cancel(handle: Long)
        fun release(handle: Long)
        fun stats(handle: Long): LongArray
    }
    init { require(spec.hiddenWidth == 1024 && spec.positionsPerFrame == 16 && spec.kvCacheCapacity == 32) }
    private val handles = mutableSetOf<Long>()
    private var loaded = false
    private var closed = false

    @Synchronized override fun loadContext(asset: String): Long {
        check(!closed)
        val file = File(asset)
        require(file.isFile && file.length() in 1..(512L * 1024 * 1024))
        require(file.length() + 16L * 1024 * 1024 < Runtime.getRuntime().maxMemory()) { "Insufficient diagnostic Java heap for immutable context snapshot" }
        // Hash the same privately owned bytes passed to native, avoiding a file re-read race.
        val bytes = file.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(sha == spec.sha256) { "CP context SHA-256 mismatch" }
        val paths = listOf("libQnnHtp.so", "libQnnSystem.so").map { name ->
            File(runtime, name).also { require(it.isFile) { "Missing existing SDK runtime" } }.canonicalPath
        }
        for (name in listOf("libcdsprpc.so", "libadsprpc.so", "libhidlbase.so", "libvndksupport.so")) {
            require(!File(runtime, name).exists()) { "Use public vendor RPC; do not bundle RPC or private OS dependencies" }
        }
        require(File(runtime, "libQnnHtpV79Stub.so").isFile) { "Missing matching SDK HTP stub" }
        if (!loaded) { bridge.loadLibrary(library); bridge.prepareRuntime(runtime); loaded = true }
        // Android exposes the vendor RPC library by SONAME in its public namespace.
        val handle = bridge.create(bytes, paths[0], paths[1], "libcdsprpc.so")
        check(handle > 0 && handle !in handles) { "Invalid native context token" }
        handles += handle
        return handle
    }
    @Synchronized override fun resetKv(context: Long) {
        owned(context); bridge.reset(context)
    }
    @Synchronized override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {
        owned(context)
        require(position in 0..15 && input.size == 1024 && output.size == 1024 && input.all(Float::isFinite))
        bridge.execute(context, position, input, output)
        check(output.all(Float::isFinite)) { "Invalid CP output" }
    }
    @Synchronized fun cancel(context: Long) { owned(context); bridge.cancel(context) }
    @Synchronized fun stats(context: Long): LongArray {
        owned(context)
        return bridge.stats(context).also { check(it.size == 7) }.copyOf()
    }
    @Synchronized override fun release(context: Long) {
        if (context in handles) { bridge.release(context); handles -= context }
    }
    private fun owned(handle: Long) { check(!closed && handle in handles) { "No owned diagnostic context" } }
    @Synchronized override fun close() {
        for (handle in handles.toList()) release(handle)
        closed = true
    }
}

internal object ResidentCpRustJni : ResidentCpRustNativeApi.Bridge {
    override fun loadLibrary(file: File?) {
        if (file == null) System.loadLibrary("lami_resident_cp_diag") else System.load(file.canonicalPath)
    }
    override fun prepareRuntime(runtime: File) {
        // Keep the app's existing ClassLoader namespace; RPC resolves natively by SONAME.
        System.load(File(runtime, "libQnnHtpV79Stub.so").canonicalPath)
    }
    override fun create(bytes: ByteArray, backend: String, system: String, rpc: String) = nativeCreate(bytes, backend, system, rpc)
    override fun reset(handle: Long) = nativeReset(handle)
    override fun execute(handle: Long, position: Int, input: FloatArray, output: FloatArray) = nativeStep(handle, position, input, output)
    override fun cancel(handle: Long) = nativeCancel(handle)
    override fun release(handle: Long) = nativeRelease(handle)
    override fun stats(handle: Long) = nativeStats(handle)
    internal fun diagnosticLog(): String = nativeDiagnosticLog()
    private external fun nativeDiagnosticLog(): String
    private external fun nativeCreate(bytes: ByteArray, backend: String, system: String, rpc: String): Long
    private external fun nativeReset(handle: Long)
    private external fun nativeStep(handle: Long, position: Int, input: FloatArray, output: FloatArray)
    private external fun nativeCancel(handle: Long)
    private external fun nativeRelease(handle: Long)
    private external fun nativeStats(handle: Long): LongArray
}
