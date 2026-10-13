package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalMultiImageFixture(val label: String, val color: String, val shape: String, val dotCount: Int = 0)
internal data class LocalMultiImageRecognition(
    val fixture: LocalMultiImageFixture,
    val body: String?,
    val colorCorrect: Boolean,
    val shapeCorrect: Boolean,
) {
    val correct: Boolean get() = colorCorrect && shapeCorrect
}

internal data class LocalMultiImageAssessment(
    val labels: List<String>,
    val missing: List<String>,
    val duplicates: List<String>,
    val unexpected: List<String>,
    val ordered: Boolean,
    val recognitions: List<LocalMultiImageRecognition>,
    val distinctOrdered: Boolean,
) {
    val allRecognized: Boolean get() = missing.isEmpty() && duplicates.isEmpty() && unexpected.isEmpty() && recognitions.all { it.correct }
    val endpointsRecognized: Boolean get() = recognitions.firstOrNull()?.correct == true && recognitions.lastOrNull()?.correct == true
}

internal data class LocalMultiImageDotAssessment(
    val labels: List<String>, val missing: List<String>, val ordered: Boolean,
    val reported: Map<String, Int?>, val correct: Map<String, Boolean>,
) {
    val allCorrect: Boolean get() = ordered && correct.values.all { it }
}

internal object LocalMultiImageAssessmentPolicy {
    private val line = Regex("^\\s*(?:(?:[-*]|\\d+[.)])\\s*)?(?:label\\s*:\\s*)?([A-J])\\s*[:.)-]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val tableRow = Regex("^\\s*\\|\\s*([A-J])\\s*\\|\\s*([^|]+)\\|\\s*([^|]+)\\|?\\s*$", RegexOption.IGNORE_CASE)

    private fun rows(response: String): List<Pair<String, String>> = response
        .replace(Regex("[,;]\\s*(?=(?:label\\s*:\\s*)?[A-J]\\s*:)", RegexOption.IGNORE_CASE), "\n")
        .lineSequence().mapNotNull { raw ->
        val plain = raw.replace("**", "").replace("`", "")
        line.find(plain)?.let { it.groupValues[1].uppercase() to it.groupValues[2].lowercase() }
            ?: tableRow.find(plain)?.let { it.groupValues[1].uppercase() to "${it.groupValues[2]}:${it.groupValues[3]}".lowercase() }
    }.toList()

    fun assess(response: String, fixtures: List<LocalMultiImageFixture>): LocalMultiImageAssessment {
        val rows = rows(response)
        val labels = rows.map { it.first }
        val expected = fixtures.map { it.label }
        return LocalMultiImageAssessment(
            labels = labels,
            missing = expected.filter { it !in labels },
            duplicates = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList(),
            unexpected = labels.filter { it !in expected }.distinct(),
            ordered = labels == expected,
            distinctOrdered = labels.distinct() == expected,
            recognitions = fixtures.map { fixture ->
                val colors = when (fixture.color) {
                    "cyan" -> listOf("cyan", "turquoise", "aqua", "light blue")
                    "magenta" -> listOf("magenta", "pink", "fuchsia")
                    else -> listOf(fixture.color)
                }
                val shapes = when (fixture.shape) {
                    "circle" -> listOf("circle", "circular")
                    "triangle" -> listOf("triangle", "triangular")
                    else -> listOf(fixture.shape)
                }
                val bodies = rows.filter { it.first == fixture.label }.map { it.second }
                val body = bodies.firstOrNull { candidate -> colors.any(candidate::contains) && shapes.any(candidate::contains) }
                    ?: bodies.firstOrNull { candidate -> shapes.any(candidate::contains) } ?: bodies.firstOrNull()
                LocalMultiImageRecognition(fixture, body,
                    body != null && colors.any(body::contains),
                    body != null && shapes.any(body::contains))
            },
        )
    }

    fun assessDots(response: String, fixtures: List<LocalMultiImageFixture>): LocalMultiImageDotAssessment {
        val rows = rows(response)
        val labels = rows.map { it.first }
        val words = mapOf("zero" to 0, "one" to 1, "two" to 2, "three" to 3)
        val reported = fixtures.associate { fixture ->
            val body = rows.singleOrNull { it.first == fixture.label }?.second
            val number = body?.let { Regex("\\b[0-9]+\\b").find(it)?.value?.toIntOrNull()
                ?: words.entries.firstOrNull { (word, _) -> Regex("\\b$word\\b").containsMatchIn(it) }?.value }
            fixture.label to number
        }
        return LocalMultiImageDotAssessment(labels, fixtures.map { it.label }.filter { it !in labels },
            labels == fixtures.map { it.label }, reported,
            fixtures.associate { it.label to (reported[it.label] == it.dotCount) })
    }
}
