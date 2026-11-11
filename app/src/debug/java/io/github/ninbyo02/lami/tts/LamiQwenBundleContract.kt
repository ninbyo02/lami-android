package io.github.ninbyo02.lami.tts

import org.json.JSONObject

/** Manifest-only eligibility checks. Weight bytes are hashed by the synthesis session. */
internal object LamiQwenBundleContract {
    const val DECODER = "speech-decoder-dynamic-et14.pte"
    const val DECODER_SHA256 = "05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5"
    const val DEFAULT_MAIN = "stateless-28-int4-cache256-et14.pte"
    const val DEFAULT_CP = "cp-stateless-fp32-cache32-et14.pte"

    private val mainPilots = mapOf(
        "stateless-28-fp32-cache256-et14.pte" to "0813bb92405360aa285560f16270319491f7757480a98cb882e897a0e4d7531a",
        "main-int8-permute-cache256.pte" to "5a03abc2481cc050ea37950247b73450fd3be64d6a4b7854dee4251b39db9bba",
        "main-int8-cache256.pte" to "6a00b9912080e057db86fe29e0c67bb3a8fa6c0851c8f5daea04175578d5c338",
        "main-int8-permute-cache128.pte" to "ec6e5027e623b054f711905113c1424b0a6de0cc7e43dc11b24ccc03b0dc1d8c",
        "main-int8-grouped-cache128.pte" to "83ef62e9ac80c6db43d532b43c7e85d0586e5860e6718c552054f321d8da5dca",
    )
    private val cpPilots = mapOf(
        "cp-int8-cache32.pte" to "8d0843096887167a64610e33569cc6c15b61dfedd19ddada23ae98c205cd1d87",
        "cp-int8-cache16.pte" to "43d29ccbd80d8f9c6e0e2cf57d84c5939a772d31262301088b73f7613e22e22c",
        "cp-int8-grouped-cache16.pte" to "54aa8ae8872e3bcbf78165f76255136627ca9de48ff02edf2bd9bb1257df1d01",
        "cp-int8-grouped-delta-cache16.pte" to "0f801eac3fbdd26db5e972d95b79e57362e75582ff09efe24a249da62ee046cf",
    )

    fun validate(data: JSONObject): Set<String> {
        require(data.getInt("version") == 2 && data.getInt("capacity") == 256) { "音声モデルの形式が対応していません。" }
        require(!data.has("speaker") || data.getString("speaker") == "lami_cute") { "LAMIかわいい声のモデルではありません。" }
        val hashes = data.getJSONObject("sha256")
        val main = data.optString("main_program", DEFAULT_MAIN)
        val cp = data.optString("cp_program", DEFAULT_CP)
        require(main == DEFAULT_MAIN || main in mainPilots) { "対応していないQwen main programです: $main" }
        require(cp == DEFAULT_CP || cp in cpPilots) { "対応していないQwen CP programです: $cp" }
        mainPilots[main]?.let { require(hashes.getString(main) == it) { "未検証のQwen main programです。" } }
        cpPilots[cp]?.let { require(hashes.getString(cp) == it) { "未検証のQwen CP programです。" } }
        require(hashes.getString(DECODER) == DECODER_SHA256) { "検証済みのcausal PCM decoderが必要です。" }
        require(data.getInt("decoder_frames_min") == 2 && data.getInt("decoder_frames_max") == 256 &&
            data.getInt("samples_per_frame") == 1920 && data.getInt("sample_rate") == 24_000) { "Qwen decoderの形式が対応していません。" }
        val prepacked = data.optBoolean("cp_head_prepacked4", false)
        require(!prepacked || !data.optBoolean("cp_head_fixed_benchmark", false)) { "Packed CP headsではfixed benchmarkを使えません。" }
        val required = buildSet {
            addAll(listOf(main, cp, DECODER, "main.head.f32", "main.embedding.f32",
                "text_frontend/vocab.json", "text_frontend/merges.txt",
                "text_frontend/tokenizer_config.json", "text_frontend/projected-text.f32"))
            for (index in 0..14) {
                add(if (prepacked) "cp.head.$index.packed4.f32" else "cp.head.$index.f32")
                add("cp.embedding.$index.f32")
            }
        }
        require(required.all(hashes::has)) { "音声モデルの必須ファイル一覧が不足しています: ${required.filterNot(hashes::has).joinToString()}" }
        require(!hashes.has("voice-text-bundle.json")) { "音声モデルのmanifestは自身を参照できません。" }
        val capacity = if (main in setOf("main-int8-permute-cache128.pte", "main-int8-grouped-cache128.pte")) 128 else 256
        for (name in listOf("rope_cos", "rope_sin")) {
            val positions = data.getJSONArray(name)
            require(positions.length() >= capacity) { "Qwen $name の位置が不足しています。" }
            for (position in 0 until capacity) {
                val row = positions.getJSONArray(position)
                require(row.length() == 128) { "Qwen $name の幅が不正です。" }
                for (index in 0 until 128) {
                    val value = row.get(index)
                    require(value is Number && value.toFloat().isFinite()) { "Qwen $name の値が不正です。" }
                }
            }
        }
        return required
    }
}
