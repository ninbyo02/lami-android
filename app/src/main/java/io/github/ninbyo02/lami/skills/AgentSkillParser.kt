package io.github.ninbyo02.lami.skills

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException

/** Parses the standard document format; metadata never grants tools or execution rights. */
object AgentSkillParser {
    private const val MAX_DOCUMENT_CHARACTERS = 128 * 1024
    private const val MAX_FRONTMATTER_CHARACTERS = 16 * 1024
    private val namePattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    fun parse(text: String): AgentSkillDefinition {
        require(text.length <= MAX_DOCUMENT_CHARACTERS) { "スキルのファイルが大きすぎます。" }
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").split('\n')
        require(lines.firstOrNull()?.trimEnd() == "---") { "スキルのYAMLメタデータがありません。" }
        val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }
            ?: throw IllegalArgumentException("スキルのYAMLメタデータが閉じられていません。")
        val frontmatter = lines.subList(1, end).joinToString("\n")
        require(frontmatter.length <= MAX_FRONTMATTER_CHARACTERS) { "スキルのメタデータが大きすぎます。" }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 12
            codePointLimit = MAX_FRONTMATTER_CHARACTERS
        }
        val fields = try {
            Yaml(SafeConstructor(options)).load<Any?>(frontmatter) as? Map<*, *>
        } catch (_: YAMLException) {
            throw IllegalArgumentException("スキルのYAMLメタデータを読み込めません。")
        } ?: throw IllegalArgumentException("スキルのメタデータはマップ形式で指定してください。")
        require(fields.keys.all { it is String }) { "スキルのメタデータ項目名を確認してください。" }

        fun string(name: String, required: Boolean = false, maximum: Int? = null): String? {
            if (!fields.containsKey(name) && !required) return null
            val value = fields[name] as? String
                ?: throw IllegalArgumentException("スキルの$name は文字列で指定してください。")
            require(value.isNotBlank() && (maximum == null || value.codePointCount(0, value.length) <= maximum)) {
                "スキルの$name の長さを確認してください。"
            }
            return value
        }

        val name = requireNotNull(string("name", required = true, maximum = 64))
        require(namePattern.matches(name)) { "スキル名は小文字の英数字と単一のハイフンで指定してください。" }
        val description = requireNotNull(string("description", required = true, maximum = 1024))
        val body = lines.subList(end + 1, lines.size).joinToString("\n").trim('\n')
        require(body.isNotBlank()) { "スキルの手順がありません。" }
        val metadata = if (!fields.containsKey("metadata")) emptyMap() else {
            val values = fields["metadata"] as? Map<*, *>
                ?: throw IllegalArgumentException("スキルのmetadataは文字列のマップで指定してください。")
            require(values.all { (key, value) -> key is String && value is String }) {
                "スキルのmetadataは文字列のマップで指定してください。"
            }
            values.entries.associate { (key, value) -> key as String to value as String }
        }
        return AgentSkillDefinition(
            name = name,
            description = description,
            body = body,
            metadata = metadata,
            license = string("license"),
            compatibility = string("compatibility", maximum = 500),
            allowedTools = string("allowed-tools"),
        )
    }
}
