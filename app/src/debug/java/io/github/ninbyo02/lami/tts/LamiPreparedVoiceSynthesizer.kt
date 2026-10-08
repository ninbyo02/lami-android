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

    /** Arbitrary Japanese sentence, bounded by the selected diagnostic main cache. */
    suspend fun generateText(root: File, text: String, session: LamiVoiceModuleCache.Session, reuseCpWorkspace: Boolean = true, onPrefix: (suspend (LamiVoiceCodes) -> Unit)? = null, progress: (String) -> Unit = {}): LamiVoiceCodes {
        val timing = WorkTiming()
        progress("stage=hash_validation")
        val ctx = JSONObject(root.resolve("voice-text-bundle.json").readText())
        require(ctx.getInt("version") == 2 && ctx.getInt("capacity") == 256)
        val files = ctx.getJSONObject("sha256")
        session.verifyBundle(root, files, progress)
        val preparationStarted = android.os.SystemClock.elapsedRealtime()
        val head = Matrix(root.resolve("main.head.f32"), 3072, timing, "main_head")
        val embedding = Matrix(root.resolve("main.embedding.f32"), 3072)
        val projected = Matrix(root.resolve("text_frontend/projected-text.f32"), 151936)
        val frontend = LamiVoiceTextFrontend(session.tokenizer(root.resolve("text_frontend"), progress), projected::row, embedding::row)
        val prepared = frontend.prepare(text)
        val prepacked = ctx.optBoolean("cp_head_prepacked4", false)
        require(!prepacked || !ctx.optBoolean("cp_head_fixed_benchmark", false)) { "Fixed benchmark requires row-major heads" }
        val cpHeads = (0..14).map {
            val name = if (prepacked) "cp.head.$it.packed4.f32" else "cp.head.$it.f32"
            require(!prepacked || files.has(name)) { "Unverified packed head: $name" }
            Matrix(root.resolve(name), 2048, timing, "cp_heads", packed4 = ctx.optBoolean("cp_head_packed4", false), prepacked4 = prepacked)
        }
        if (ctx.optBoolean("cp_head_fixed_benchmark", false)) {
            cpHeads[0].benchmarkPacked4(progress)
        }
        val cpEmbeddings = (0..14).map { Matrix(root.resolve("cp.embedding.$it.f32"), 2048) }
        val frames = mutableListOf<LongArray>()
        val codecAllowed = (0..2047).toSet()
        val codecWithEos = codecAllowed + 2150
        val seen = mutableSetOf<Int>()
        val sampler = LamiVoiceCodecSampler()
        var endedOnEos = false
        val mainProgram = ctx.optString("main_program", "stateless-28-int4-cache256-et14.pte")
        require(mainProgram.matches(Regex("[A-Za-z0-9._-]+")) && files.has(mainProgram)) { "Main program absent from verified bundle" }
        val mainCapacity = when (mainProgram) {
            "main-int8-permute-cache128.pte" -> {
                require(files.getString(mainProgram) == "ec6e5027e623b054f711905113c1424b0a6de0cc7e43dc11b24ccc03b0dc1d8c") { "Unverified main cache128 pilot" }
                128
            }
            "main-int8-grouped-cache128.pte" -> {
                require(files.getString(mainProgram) == "83ef62e9ac80c6db43d532b43c7e85d0586e5860e6718c552054f321d8da5dca") { "Unverified main grouped pilot" }
                128
            }
            else -> 256
        }
        require(prepared.prefill.size < mainCapacity) { "Text leaves no generation space in selected main cache" }
        progress("metric=main_program name=$mainProgram capacity=$mainCapacity")
        progress("metric=text_prepare ms=${android.os.SystemClock.elapsedRealtime() - preparationStarted}")
        val cpProgram = ctx.optString("cp_program", "cp-stateless-fp32-cache32-et14.pte")
        require(cpProgram in setOf("cp-stateless-fp32-cache32-et14.pte", "cp-int8-cache32.pte", "cp-int8-cache16.pte", "cp-int8-grouped-cache16.pte") && files.has(cpProgram)) { "CP program absent from verified diagnostic bundle" }
        if (cpProgram == "cp-int8-cache32.pte") {
            require(files.getString(cpProgram) == "8d0843096887167a64610e33569cc6c15b61dfedd19ddada23ae98c205cd1d87") { "Unreviewed CP INT8 pilot" }
        }
        if (cpProgram == "cp-int8-cache16.pte") {
            require(files.getString(cpProgram) == "43d29ccbd80d8f9c6e0e2cf57d84c5939a772d31262301088b73f7613e22e22c") { "Unverified CP cache16 pilot" }
        }
        if (cpProgram == "cp-int8-grouped-cache16.pte") {
            require(files.getString(cpProgram) == "54aa8ae8872e3bcbf78165f76255136627ca9de48ff02edf2bd9bb1257df1d01") { "Unverified grouped CP pilot" }
        }
        // Each codec frame uses CP positions 0..15, including the initial hidden step.
        val cpCapacity = if (cpProgram in setOf("cp-int8-cache16.pte", "cp-int8-grouped-cache16.pte")) 16 else 32
        progress("metric=cp_program name=$cpProgram capacity=$cpCapacity")
        session.useModule(root.resolve(mainProgram), progress) { main ->
            session.useModule(root.resolve(cpProgram), progress) { cp ->
                val mainCache = Decoder(main, 28, mainCapacity, 3, ctx, timing, "main")
                var h = FloatArray(WIDTH)
                val prefillStarted = android.os.SystemClock.elapsedRealtime()
                prepared.prefill.forEachIndexed { position, input -> h = mainCache.step(input, position) }
                progress("metric=prefill ms=${android.os.SystemClock.elapsedRealtime() - prefillStarted}")
                // Reuse the CP workspace across frames; reset its state before each frame.
                val reusableCp = if (reuseCpWorkspace) Decoder(cp, 5, cpCapacity, 1, ctx, timing, "cp") else null
                progress("metric=cp_workspace reused=$reuseCpWorkspace")
                val codecStarted = android.os.SystemClock.elapsedRealtime()
                val frameMillis = mutableListOf<Long>()
                val limit = mainCapacity - prepared.prefill.size
                for (frame in 0 until limit) {
                    progress("stage=codec frame=$frame limit=$limit")
                    val frameStarted = System.nanoTime()
                    val allowed = if (frame >= 2) codecWithEos else codecAllowed
                    val mainScores = head.logits(h)
                    val mainSampleStarted = System.nanoTime()
                    val token = sampler.choose(mainScores, allowed, seen)
                    timing.record("main_sampling", mainSampleStarted)
                    if (token == 2150) { endedOnEos = true; break }
                    seen += token
                    val row = LongArray(16)
                    row[0] = token.toLong()
                    val last = embedding.row(token)
                    val sum = last.copyOf()
                    val cpCache = reusableCp?.also { it.reset() } ?: Decoder(cp, 5, cpCapacity, 1, ctx, timing, "cp")
                    cpCache.step(h, 0)
                    var ch = cpCache.step(last, 1)
                    for (group in 0..14) {
                        val cpScores = cpHeads[group].logits(ch)
                        val cpSampleStarted = System.nanoTime()
                        val q = sampler.choose(cpScores, codecAllowed)
                        timing.record("cp_sampling", cpSampleStarted)
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
                    frameMillis += (System.nanoTime() - frameStarted) / 1_000_000
                    if (onPrefix != null && frames.size % 8 == 0) {
                        onPrefix(LamiVoiceCodes(LongArray(frames.size * 16) { index -> frames[index % frames.size][index / frames.size] }, frames.size))
                    }
                }
                if (frameMillis.isNotEmpty()) {
                    val sorted = frameMillis.sorted()
                    val overBudget = sorted.count { it > 80L }
                    progress("metric=frame_budget frames=${sorted.size} p50_ms=${sorted[(sorted.size - 1) / 2]} p95_ms=${sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]} max_ms=${sorted.last()} over_80ms=$overBudget")
                }
                progress("metric=codec frames=${frames.size} ms=${android.os.SystemClock.elapsedRealtime() - codecStarted}")
            }
        }
        timing.report(progress)
        check(endedOnEos) { if (onPrefix == null) "Speech did not reach EOS within model cache; incomplete audio withheld" else "Speech stream did not reach EOS; provisional audio stopped" }
        require(frames.size in 2..256)
        return LamiVoiceCodes(LongArray(frames.size * 16) { i -> frames[i % frames.size][i / frames.size] }, frames.size)
    }

    private fun vector(array: JSONArray): FloatArray {
        require(array.length() == WIDTH)
        return FloatArray(WIDTH) { array.getDouble(it).toFloat() }.also { require(it.all(Float::isFinite)) }
    }

    private class WorkTiming {
        private val totals = linkedMapOf<String, Long>()
        private val calls = linkedMapOf<String, Int>()
        fun record(name: String, started: Long) {
            totals[name] = (totals[name] ?: 0L) + (System.nanoTime() - started)
            calls[name] = (calls[name] ?: 0) + 1
        }
        fun report(progress: (String) -> Unit) {
            totals.forEach { (name, nanos) -> progress("metric=$name calls=${calls[name]} ms=${nanos / 1_000_000}") }
        }
    }

    private class Matrix(file: File, private val rows: Int, private val timing: WorkTiming? = null, private val label: String = "head", private val packed4: Boolean = false, private val prepacked4: Boolean = false) {
        private val data: FloatBuffer = RandomAccessFile(file, "r").use {
            require(it.length() == rows * WIDTH * 4L) { "Invalid tensor file: ${file.name}" }
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        }
        // Diagnostic opt-in: packing costs extra memory and is performed once per request.
        private val packedData = if (prepacked4) {
            require(rows % 4 == 0)
            data
        } else if (packed4) {
            require(rows % 4 == 0)
            val started = System.nanoTime()
            val buffer = ByteBuffer.allocateDirect(rows * WIDTH * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            LamiVoiceMatrixKernels.pack4(data, buffer, rows)
            timing?.record("${label}_pack", started)
            buffer
        } else null
        fun row(index: Int): FloatArray {
            check(!prepacked4) { "Packed heads do not expose embedding rows" }
            require(index in 0 until rows)
            return FloatArray(WIDTH) { data.get(index * WIDTH + it) }
        }
        suspend fun logits(h: FloatArray): FloatArray {
            currentCoroutineContext().ensureActive()
            val started = System.nanoTime()
            val scores = packedData?.let { LamiVoiceMatrixKernels.logitsPacked4(it, h, rows) }
                ?: LamiVoiceMatrixKernels.logits(data, h, rows)
            timing?.record(label, started)
            check(scores.size == rows && scores.all(Float::isFinite)) { "Non-finite codec logits" }
            currentCoroutineContext().ensureActive()
            return scores
        }
        suspend fun benchmarkPacked4(progress: (String) -> Unit) {
            check(!prepacked4)
            require(rows % 4 == 0)
            val packed = packedData ?: ByteBuffer.allocateDirect(rows * WIDTH * 4)
                .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().also {
                    LamiVoiceMatrixKernels.pack4(data, it, rows)
                }
            // Fixed inputs and one real CP head; alternate adjacent trial order.
            for (inputIndex in 0..2) {
                val h = FloatArray(WIDTH) { j -> ((j * (inputIndex + 3) % 101) - 50) / 37f }
                val expected = LamiVoiceMatrixKernels.logits(data, h, rows)
                for (trial in 0..6) {
                    for (usePacked in if (trial % 2 == 0) listOf(false, true) else listOf(true, false)) {
                        currentCoroutineContext().ensureActive()
                        fun calculate() = if (usePacked) LamiVoiceMatrixKernels.logitsPacked4(packed, h, rows)
                            else LamiVoiceMatrixKernels.logits(data, h, rows)
                        repeat(3) { calculate() }
                        val outputs = arrayOfNulls<FloatArray>(16)
                        val started = System.nanoTime()
                        repeat(outputs.size) { outputs[it] = calculate() }
                        val elapsed = System.nanoTime() - started
                        check(outputs.all { output -> output != null && output.indices.all { output[it].toBits() == expected[it].toBits() } }) {
                            "Fixed-input CP head mismatch; diagnostic audio withheld"
                        }
                        progress("metric=cp_head_fixed input=$inputIndex trial=$trial packed=$usePacked calls=16 total_ns=$elapsed bit_exact=true")
                    }
                }
            }
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

    private class Decoder(private val module: Module, layers: Int, private val capacity: Int, private val axes: Int, private val context: JSONObject, private val timing: WorkTiming? = null, private val label: String = "decoder") {
        private val shape = longArrayOf(layers.toLong(), 1, 8, capacity.toLong(), 128)
        // Own input storage: outputs may be overwritten on the next forward.
        // Copy directly into these buffers instead of allocating heap arrays and
        // then copying those arrays into newly allocated tensors every step.
        private val kBuffer = Tensor.allocateFloatBuffer(layers * 8 * capacity * 128)
        private val vBuffer = Tensor.allocateFloatBuffer(layers * 8 * capacity * 128)
        private val k = Tensor.fromBlob(kBuffer, shape)
        private val v = Tensor.fromBlob(vBuffer, shape)
        // One zero block per workspace, instead of two new direct buffers per frame.
        private val zeroCache by lazy { FloatArray(kBuffer.capacity()) }
        fun reset() {
            val started = System.nanoTime()
            kBuffer.clear()
            vBuffer.clear()
            kBuffer.put(zeroCache)
            vBuffer.put(zeroCache)
            kBuffer.rewind()
            vBuffer.rewind()
            timing?.record("${label}_reset", started)
        }
        // CP visits the same positions 0..15 for every codec frame.
        // Keep immutable position inputs and own mutable hidden storage per decoder.
        private data class PositionInputs(val cosine: EValue, val sine: EValue, val mask: EValue, val position: EValue)
        private val positionInputs = arrayOfNulls<PositionInputs>(capacity)
        private val hiddenBuffer = Tensor.allocateFloatBuffer(WIDTH)
        private val hiddenInput = EValue.from(Tensor.fromBlob(hiddenBuffer, longArrayOf(1, 1, WIDTH.toLong())))
        private fun inputsAt(position: Int): PositionInputs = positionInputs[position] ?: run {
            val cosine = context.getJSONArray("rope_cos").getJSONArray(position)
            val sine = context.getJSONArray("rope_sin").getJSONArray(position)
            val c = FloatArray(axes * 128) { cosine.getDouble(it % 128).toFloat() }
            val s = FloatArray(axes * 128) { sine.getDouble(it % 128).toFloat() }
            val ropeShape = if (axes == 3) longArrayOf(3, 1, 1, 128) else longArrayOf(1, 1, 128)
            PositionInputs(
                EValue.from(Tensor.fromBlob(c, ropeShape)),
                EValue.from(Tensor.fromBlob(s, ropeShape)),
                EValue.from(Tensor.fromBlob(FloatArray(capacity) { if (it <= position) 0f else -1e9f }, longArrayOf(1, 1, 1, capacity.toLong()))),
                EValue.from(Tensor.fromBlob(longArrayOf(position.toLong()), longArrayOf(1))),
            ).also { positionInputs[position] = it }
        }
        suspend fun step(h: FloatArray, position: Int): FloatArray {
            currentCoroutineContext().ensureActive()
            require(position in 0 until capacity)
            val preparationStarted = System.nanoTime()
            require(h.size == WIDTH)
            val inputs = inputsAt(position)
            hiddenBuffer.clear()
            hiddenBuffer.put(h)
            hiddenBuffer.rewind()
            timing?.record("${label}_input_prepare", preparationStarted)
            val forwardStarted = System.nanoTime()
            val out = module.forward(
                hiddenInput, EValue.from(k), EValue.from(v),
                inputs.cosine, inputs.sine, inputs.mask, inputs.position,
            )
            timing?.record("${label}_forward", forwardStarted)
            val copyStarted = System.nanoTime()
            require(out.size == 3)
            // Copy outputs: ExecuTorch reuses output storage on the next forward.
            val nextH = out[0].toTensor().dataAsFloatArray
            require(nextH.size == WIDTH && nextH.all(Float::isFinite))
            val nextK = out[1].toTensor()
            val nextV = out[2].toTensor()
            require(nextK.numel() == kBuffer.capacity().toLong() && nextV.numel() == vBuffer.capacity().toLong())
            kBuffer.clear()
            vBuffer.clear()
            nextK.copyDataInto(kBuffer)
            nextV.copyDataInto(vBuffer)
            kBuffer.rewind()
            vBuffer.rewind()
            timing?.record("${label}_output_copy", copyStarted)
            currentCoroutineContext().ensureActive()
            return nextH
        }
    }
}

internal data class LamiVoiceCodes(val values: LongArray, val frames: Int)
