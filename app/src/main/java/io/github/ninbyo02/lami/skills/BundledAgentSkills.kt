package io.github.ninbyo02.lami.skills

import android.content.Context
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only shipped skills can be activated. This is not a ZIP importer or script executor. */
class BundledAgentSkills internal constructor(private val readAsset: (String) -> String) {
    constructor(context: Context) : this({ path ->
        context.applicationContext.assets.open(path).use { input ->
            val bytes = input.readNBytes(MAX_ASSET_BYTES + 1)
            require(bytes.size <= MAX_ASSET_BYTES) { "同梱スキルのファイルが大きすぎます。" }
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }
    })

    companion object {
        private const val MAX_ASSET_BYTES = 256 * 1024
        private val bundledNames = setOf("web-search")
        private val referencePath = Regex("references/[A-Za-z0-9_-]+\\.md")
    }

    fun load(name: String): AgentSkillDefinition {
        requireBundled(name)
        val definition = AgentSkillParser.parse(read("skills/$name/SKILL.md"))
        require(definition.name == name) { "スキル名と同梱ディレクトリ名が一致しません。" }
        return definition
    }

    fun readReference(name: String, path: String): String {
        requireBundled(name)
        require(referencePath.matches(path)) { "同梱スキル内の参照ファイルを指定してください。" }
        return read("skills/$name/$path").also {
            require(it.isNotBlank()) { "同梱スキルの参照ファイルが空です。" }
        }
    }

    /** Disabled skills are neither loaded nor disclosed to the active catalog. */
    fun activeCatalog(enabledNames: Set<String>): List<AgentSkillDefinition> {
        require(enabledNames.all { it in bundledNames }) { "未対応の同梱スキルです。" }
        return enabledNames.sorted().map(::load)
    }

    private fun requireBundled(name: String) {
        require(name in bundledNames) { "未対応の同梱スキルです。" }
    }

    private fun read(path: String): String = try {
        readAsset(path).also {
            require(it.length <= MAX_ASSET_BYTES) { "同梱スキルのファイルが大きすぎます。" }
        }
    } catch (_: IOException) {
        throw IOException("同梱スキルのファイルを読み込めません。")
    }
}
