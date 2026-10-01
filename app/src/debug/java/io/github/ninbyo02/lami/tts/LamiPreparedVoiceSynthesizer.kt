package io.github.ninbyo02.lami.tts

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Bounded 0.6B diagnostic; fixed golden probe and seeded arbitrary-text probe.
 * CPU matrix heads prioritize correctness over speed.
 */
internal object LamiPreparedVoiceSynthesizer {
    private const val WIDTH = 1024
    private const val FRAMES = 31
    suspend fun generate(root: File, progress: (String) -> Unit = {}): LongArray {
        progress("stage=hash_validation")
        val trace = root.resolve("device-parity-trace").takeIf { root.resolve("device-parity-trace.enabled").isFile }
        trace?.mkdirs()
        fun dump(name: String, values: FloatArray) {
            if (trace == null) return
            val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(bytes::putFloat)
            trace.resolve("$name.f32").writeBytes(bytes.array())
        }
        val ctx = JSONObject(root.resolve("prepared-hai.json").readText())
        require(ctx.getInt("version") == 1 && ctx.getString("text") == "はい。")
        val files = ctx.getJSONObject("sha256")
        for (name in files.keys()) {
            currentCoroutineContext().ensureActive()
            require(name.matches(Regex("[A-Za-z0-9._-]+")))
            val digest = MessageDigest.getInstance("SHA-256")
            root.resolve(name).inputStream().use { input ->
                val bytes = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == files.getString(name)) { "Model hash mismatch: $name" }
        }
        progress("stage=prefill")
        val prefill = ctx.getJSONArray("prefill")
        require(prefill.length() in 1..(64 - FRAMES))
        val trailing = ctx.getJSONArray("trailing")
        val pad = vector(ctx.getJSONArray("pad"))
        val head = Matrix(root.resolve("main.head.f32"), 3072)
        val embedding = Matrix(root.resolve("main.embedding.f32"), 3072)
        val cpHeads = (0..14).map { Matrix(root.resolve("cp.head.$it.f32"), 2048) }
        val cpEmbeddings = (0..14).map { Matrix(root.resolve("cp.embedding.$it.f32"), 2048) }
        val result = LongArray(16 * FRAMES)
        Module.load(root.resolve("stateless-28-int4-cache64-et14.pte").absolutePath, Module.LOAD_MODE_MMAP).use { main ->
            Module.load(root.resolve("cp-stateless-fp32-cache32-et14.pte").absolutePath, Module.LOAD_MODE_MMAP).use { cp ->
                val mainCache = Decoder(main, 28, 64, 3, ctx)
                var h = FloatArray(WIDTH)
                for (i in 0 until prefill.length()) {
                    h = mainCache.step(vector(prefill.getJSONArray(i)), i)
                    dump("main-prefill-$i", h)
                }
                // Explicit fixed-probe counterfactual: replay host main hidden into CP.
                val replay = root.resolve("device-parity-replay.f32")
                if (trace != null && replay.isFile) {
                    val bytes = replay.readBytes()
                    require(bytes.size == WIDTH * 4)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    h = FloatArray(WIDTH) { buffer.float }
                    require(h.all(Float::isFinite))
                    progress("stage=parity_replay diagnostic_only=true")
                }
                for (frame in 0 until FRAMES) {
                    progress("stage=codec frame=$frame limit=$FRAMES")
                    currentCoroutineContext().ensureActive()
                    if (frame == 0) dump("main-head", head.logits(h))
                    val token = head.argmax(h)
                    check(token in 0..2047) { "EOS/special token before fixed decoder frame count at $frame: $token" }
                    result[frame] = token.toLong()
                    val last = embedding.row(token)
                    val sum = last.copyOf()
                    val cpCache = Decoder(cp, 5, 32, 1, ctx)
                    cpCache.step(h, 0)
                    var ch = cpCache.step(last, 1)
                    for (group in 0..14) {
                        currentCoroutineContext().ensureActive()
                        if (frame == 0) {
                            dump("cp-hidden-$group", ch)
                            dump("cp-logits-$group", cpHeads[group].logits(ch))
                        }
                        val q = cpHeads[group].argmax(ch)
                        result[(group + 1) * FRAMES + frame] = q.toLong()
                        val e = cpEmbeddings[group].row(q)
                        for (j in 0 until WIDTH) sum[j] += e[j]
                        if (group < 14) ch = cpCache.step(e, group + 2)
                    }
                    if (frame < FRAMES - 1) {
                        val text = if (frame < trailing.length()) vector(trailing.getJSONArray(frame)) else pad
                        for (j in 0 until WIDTH) sum[j] += text[j]
                        h = mainCache.step(sum, prefill.length() + frame)
                    }
                }
            }
        }
        val expected = ctx.getJSONArray("expected_codes_channel_major")
        require(expected.length() == result.size)
        root.resolve("device-fixed-hai-codes.json").writeText(JSONArray(result.toList()).toString())
        check(!(trace != null && root.resolve("device-parity-replay.f32").isFile)) { "Parity replay is diagnostic only; audio withheld" }
        val mismatches = result.indices.filter { result[it] != expected.getLong(it) }
        check(mismatches.isEmpty()) { "Generated codes differ: ${mismatches.size}/${result.size}, first=${mismatches.first()}; audio withheld" }
        return result
    }

    /** Arbitrary Japanese sentence, bounded by the exported 256-slot main cache. */
    suspend fun generateText(root: File, text: String, progress: (String) -> Unit = {}): LamiVoiceCodes {
        progress("stage=hash_validation")
        val ctx = JSONObject(root.resolve("voice-text-bundle.json").readText())
        require(ctx.getInt("version") == 2 && ctx.getInt("capacity") == 256)
        val files = ctx.getJSONObject("sha256")
        for (name in files.keys()) {
            currentCoroutineContext().ensureActive()
            val file = root.resolve(name).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator)) { "Model path outside bundle" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == files.getString(name)) { "Model hash mismatch: $name" }
        }
        val head = Matrix(root.resolve("main.head.f32"), 3072)
        val embedding = Matrix(root.resolve("main.embedding.f32"), 3072)
        val projected = Matrix(root.resolve("text_frontend/projected-text.f32"), 151936)
        val frontend = LamiVoiceTextFrontend(LamiQwenTokenizer.load(root.resolve("text_frontend")), projected::row, embedding::row)
        val prepared = frontend.prepare(text)
        val cpHeads = (0..14).map { Matrix(root.resolve("cp.head.$it.f32"), 2048) }
        val cpEmbeddings = (0..14).map { Matrix(root.resolve("cp.embedding.$it.f32"), 2048) }
        val frames = mutableListOf<LongArray>()
        val seen = mutableSetOf<Int>()
        val sampler = LamiVoiceCodecSampler()
        var endedOnEos = false
        val mainProgram = ctx.optString("main_program", "stateless-28-int4-cache256-et14.pte")
        require(mainProgram.matches(Regex("[A-Za-z0-9._-]+")) && files.has(mainProgram)) { "Main program absent from verified bundle" }
        Module.load(root.resolve(mainProgram).absolutePath, Module.LOAD_MODE_MMAP).use { main ->
            Module.load(root.resolve("cp-stateless-fp32-cache32-et14.pte").absolutePath, Module.LOAD_MODE_MMAP).use { cp ->
                val mainCache = Decoder(main, 28, 256, 3, ctx)
                var h = FloatArray(WIDTH)
                prepared.prefill.forEachIndexed { position, input -> h = mainCache.step(input, position) }
                val limit = 256 - prepared.prefill.size
                for (frame in 0 until limit) {
                    progress("stage=codec frame=$frame limit=$limit")
                    val allowed = (0..2047).toMutableSet().also { if (frame >= 2) it.add(2150) }
                    val token = sampler.choose(head.logits(h), allowed, seen)
                    if (token == 2150) { endedOnEos = true; break }
                    seen += token
                    val row = LongArray(16)
                    row[0] = token.toLong()
                    val last = embedding.row(token)
                    val sum = last.copyOf()
                    val cpCache = Decoder(cp, 5, 32, 1, ctx)
                    cpCache.step(h, 0)
                    var ch = cpCache.step(last, 1)
                    for (group in 0..14) {
                        val q = sampler.choose(cpHeads[group].logits(ch), (0..2047).toSet())
                        row[group + 1] = q.toLong()
                        val e = cpEmbeddings[group].row(q)
                        for (j in 0 until WIDTH) sum[j] += e[j]
                        if (group < 14) ch = cpCache.step(e, group + 2)
                    }
                    frames += row
                    if (frame < limit - 1) {
                        for (j in 0 until WIDTH) sum[j] += prepared.pad[j]
                        h = mainCache.step(sum, prepared.prefill.size + frame)
                    }
                }
            }
        }
        check(endedOnEos) { "Speech did not reach EOS within model cache; incomplete audio withheld" }
        require(frames.size in 2..256)
        return LamiVoiceCodes(LongArray(frames.size * 16) { i -> frames[i % frames.size][i / frames.size] }, frames.size)
    }

    private fun vector(array: JSONArray): FloatArray {
        require(array.length() == WIDTH)
        return FloatArray(WIDTH) { array.getDouble(it).toFloat() }.also { require(it.all(Float::isFinite)) }
    }

    private class Matrix(file: File, private val rows: Int) {
        private val data: FloatBuffer = RandomAccessFile(file, "r").use {
            require(it.length() == rows * WIDTH * 4L) { "Invalid tensor file: ${file.name}" }
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        }
        fun row(index: Int): FloatArray {
            require(index in 0 until rows)
            return FloatArray(WIDTH) { data.get(index * WIDTH + it) }
        }
        suspend fun logits(h: FloatArray): FloatArray {
            currentCoroutineContext().ensureActive()
            val scores = LamiVoiceMatrixKernels.logits(data, h, rows)
            check(scores.size == rows && scores.all(Float::isFinite)) { "Non-finite codec logits" }
            currentCoroutineContext().ensureActive()
            return scores
        }
        suspend fun argmax(h: FloatArray, allowed: Set<Int>? = null, repeated: Set<Int> = emptySet(), penalty: Float = 1f): Int {
            val scores = logits(h)
            var best = Float.NEGATIVE_INFINITY
            var token = 0
            for (r in 0 until rows) {
                if (r % 64 == 0) currentCoroutineContext().ensureActive()
                if (allowed != null && r !in allowed) continue
                var score = scores[r]
                if (r in repeated) score = if (score < 0f) score * penalty else score / penalty
                check(score.isFinite()) { "Non-finite codec logits" }
                if (score > best) { best = score; token = r }
            }
            return token
        }
    }

    private class Decoder(private val module: Module, layers: Int, private val capacity: Int, private val axes: Int, private val context: JSONObject) {
        private val shape = longArrayOf(layers.toLong(), 1, 8, capacity.toLong(), 128)
        private var k = Tensor.fromBlob(FloatArray(layers * 8 * capacity * 128), shape)
        private var v = Tensor.fromBlob(FloatArray(layers * 8 * capacity * 128), shape)
        suspend fun step(h: FloatArray, position: Int): FloatArray {
            currentCoroutineContext().ensureActive()
            require(position in 0 until capacity)
            val c = FloatArray(axes * 128) { i -> context.getJSONArray("rope_cos").getJSONArray(position).getDouble(i % 128).toFloat() }
            val s = FloatArray(axes * 128) { i -> context.getJSONArray("rope_sin").getJSONArray(position).getDouble(i % 128).toFloat() }
            val ropeShape = if (axes == 3) longArrayOf(3, 1, 1, 128) else longArrayOf(1, 1, 128)
            val out = module.forward(
                EValue.from(Tensor.fromBlob(h, longArrayOf(1, 1, WIDTH.toLong()))), EValue.from(k), EValue.from(v),
                EValue.from(Tensor.fromBlob(c, ropeShape)), EValue.from(Tensor.fromBlob(s, ropeShape)),
                EValue.from(Tensor.fromBlob(FloatArray(capacity) { if (it <= position) 0f else -1e9f }, longArrayOf(1, 1, 1, capacity.toLong()))),
                EValue.from(Tensor.fromBlob(longArrayOf(position.toLong()), longArrayOf(1))),
            )
            require(out.size == 3)
            // Copy outputs: ExecuTorch reuses output storage on the next forward.
            val nextH = out[0].toTensor().dataAsFloatArray
            require(nextH.size == WIDTH && nextH.all(Float::isFinite))
            k = Tensor.fromBlob(out[1].toTensor().dataAsFloatArray, shape)
            v = Tensor.fromBlob(out[2].toTensor().dataAsFloatArray, shape)
            currentCoroutineContext().ensureActive()
            return nextH
        }
    }
}

internal data class LamiVoiceCodes(val values: LongArray, val frames: Int)
