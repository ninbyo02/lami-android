package io.github.ninbyo02.lami.skills

data class AgentSkillDefinition(
    val name: String,
    val description: String,
    val body: String,
    val metadata: Map<String, String> = emptyMap(),
    val license: String? = null,
    val compatibility: String? = null,
    val allowedTools: String? = null,
)
