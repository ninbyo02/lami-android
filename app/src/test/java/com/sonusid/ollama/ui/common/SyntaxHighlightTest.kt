package com.sonusid.ollama.ui.common

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {

    @Test
    fun kotlinCode_addsStylesForKeywordCommentAndString() {
        val code = """
            fun greet(name: String) {
                // hello comment
                val text = "hello"
                println(text)
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "fun"))
        assertTrue(containsStyledFragment(result, "// hello comment"))
        assertTrue(containsStyledFragment(result, "\"hello\""))
    }

    @Test
    fun kotlinGradleDslCode_highlightsFrequentDslIdentifiersAsKeywords() {
        val code = """
            plugins {
                kotlin("jvm") version "1.9.0"
                application
            }
            repositories { mavenCentral() }
            dependencies {
                implementation(kotlin("stdlib"))
                testImplementation(kotlin("test"))
            }
            application { mainClass.set("com.example.MainKt") }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "plugins"))
        assertTrue(containsStyledFragment(result, "repositories"))
        assertTrue(containsStyledFragment(result, "dependencies"))
        assertTrue(containsStyledFragment(result, "application"))
        assertTrue(containsStyledFragment(result, "implementation"))
        assertTrue(containsStyledFragment(result, "testImplementation"))
        assertTrue(containsStyledFragment(result, "mavenCentral"))
        assertTrue(containsStyledFragment(result, "mainClass"))
        assertTrue(containsStyledFragment(result, "set"))
    }


    @Test
    fun kotlinSettingsGradleDsl_detectsWithPluginManagementAndDependencyResolutionManagement() {
        val code = """
            pluginManagement {
                repositories {
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        assertTrue(containsStyledFragment(result, "repositories"))
        assertTrue(containsStyledFragment(result, "mavenCentral"))
    }

    @Test
    fun kotlinCode_withoutGradleDslMarkers_doesNotHighlightImplementationAsKeyword() {
        val code = "fun main() { val implementation = 1 }"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.indexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withSingleGradleDslBlock_doesNotEnableGradleDslKeywords() {
        val code = """
            plugins {
                kotlin("jvm") version "1.9.0"
            }
            fun main() {
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withSingleSettingsGradleDslBlock_doesNotEnableGradleDslKeywords() {
        val code = """
            pluginManagement {
                repositories {
                    mavenCentral()
                }
            }
            fun main() {
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withGradleMarkersOnlyInString_doesNotEnableGradleDslKeywords() {
        val code = """
            fun setup() {
                val gradleSnippet = "plugins {"
                val depsSnippet = "dependencies {"
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))

        val valStart = code.indexOf("val")
        val valEnd = valStart + "val".length
        assertTrue(hasStyledRangeCovering(result, valStart, valEnd))

        val pluginsStringStart = code.indexOf("\"plugins {\"")
        val pluginsStringEnd = pluginsStringStart + "\"plugins {\"".length
        assertTrue(hasStyledRangeCovering(result, pluginsStringStart, pluginsStringEnd))
    }

    @Test
    fun kotlinCode_withGradleMarkersOnlyInRawString_doesNotEnableGradleDslKeywords() {
        val code = """
            fun setup() {
                val gradleSnippet = "plugins {\nrepositories {\ndependencies {"
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))

        val valStart = code.indexOf("val")
        val valEnd = valStart + "val".length
        assertTrue(hasStyledRangeCovering(result, valStart, valEnd))

        val rawStringStart = code.indexOf("plugins {")
        val rawStringEnd = rawStringStart + "plugins {".length
        assertTrue(hasStyledRangeCovering(result, rawStringStart, rawStringEnd))
    }

    @Test
    fun kotlinCode_withGradleMarkersAtLineStartInsideTripleQuotedString_doesNotEnableGradleDslKeywords() {
        val code = buildString {
            appendLine("fun example() {")
            appendLine("    val script = \"\"\"")
            appendLine("pluginManagement {")
            appendLine("    repositories {")
            appendLine("        mavenCentral()")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("dependencyResolutionManagement {")
            appendLine("    repositories {")
            appendLine("        mavenCentral()")
            appendLine("    }")
            appendLine("}")
            appendLine("\"\"\".trimIndent()")
            appendLine("    val implementation = 42")
            appendLine("}")
        }

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        // Gradle DSL が有効になっていないことを確認（implementation がキーワード化されない）
        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withGradleMarkersOnlyInComment_doesNotEnableGradleDslKeywords() {
        val code = """
            // plugins {
            // dependencies {
            fun main() {
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withSettingsGradleMarkersOnlyInComments_doesNotEnableGradleDslKeywords() {
        val code = """
            // pluginManagement {
            /* dependencyResolutionManagement { */
            fun main() {
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withGradleDslMarkersAtLineStartWithSpacesAndTabs_enablesGradleDslKeywords() {
        val code = "\tplugins {\n    repositories{\ndependencies\t{\n    implementation(kotlin(\"stdlib\"))\n}\n"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.indexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertTrue(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withGradleDslLikeSnippetInTrimMarginString_doesNotEnableGradleDslKeywords() {
        val code = """
            fun example() {
                val s = """
                    |pluginManagement {
                    |    repositories {
                    |        mavenCentral()
                    |    }
                    |}
                    |dependencyResolutionManagement {
                    |    repositories {
                    |        mavenCentral()
                    |    }
                    |}
                """.trimMargin()
                val implementation = 1
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun kotlinCode_withGradleDslMarkersAtLineStartAsStringLiteral_doesNotEnableGradleDslKeywords() {
        val code = buildString {
            appendLine("fun example() {")
            appendLine("    val quoted = \"pluginManagement {\"")
            appendLine("    val raw = \"\"\"")
            appendLine("dependencyResolutionManagement {")
            appendLine("\"\"\".trimIndent()")
            appendLine("    val implementation = 1")
            appendLine("}")
        }

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        val implementationStart = code.lastIndexOf("implementation")
        val implementationEnd = implementationStart + "implementation".length
        assertFalse(hasStyledRangeCovering(result, implementationStart, implementationEnd))
    }

    @Test
    fun pythonCode_addsStylesForKeywordCommentAndNumber() {
        val code = """
            def add(a, b):
                # sample comment
                return a + b + 10
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "py",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "def"))
        assertTrue(containsStyledFragment(result, "# sample comment"))
        assertTrue(containsStyledFragment(result, "10"))
    }

    @Test
    fun javascriptCode_addsStylesForKeywordAndComment() {
        val code = "function x(){ return 1 } // c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "js",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "function"))
        assertTrue(containsStyledFragment(result, "return"))
        assertTrue(containsStyledFragment(result, "// c"))
    }


    @Test
    fun javascriptCode_highlightsDotPrefixedAndDecimalNumbers() {
        val code = "x = .3; y = 0.3; z = 12.34"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "js",
            colors = lightColorScheme(),
        )

        assertTrue(hasStyledRangeCovering(result, code.indexOf(".3"), code.indexOf(".3") + 2))
        assertTrue(hasStyledRangeCovering(result, code.indexOf("0.3"), code.indexOf("0.3") + 3))
        assertTrue(hasStyledRangeCovering(result, code.indexOf("12.34"), code.indexOf("12.34") + 5))
    }


    @Test
    fun javascriptCode_highlightsNegativeNumbersButNotMinusOperator() {
        val code = "a = -200; b = -0.3; c = -.5; d = a - b"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "js",
            colors = lightColorScheme(),
        )

        assertTrue(hasStyledRangeCovering(result, code.indexOf("-200"), code.indexOf("-200") + 4))
        assertTrue(hasStyledRangeCovering(result, code.indexOf("-0.3"), code.indexOf("-0.3") + 4))
        assertTrue(hasStyledRangeCovering(result, code.indexOf("-.5"), code.indexOf("-.5") + 3))

        val minusIndex = code.lastIndexOf('-')
        assertFalse(hasStyledRangeCovering(result, minusIndex, minusIndex + 1))
    }

    @Test
    fun typescriptCode_addsStylesForKeyword() {
        val code = "type A = string"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "ts",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "type"))
    }

    @Test
    fun sqlCode_addsStylesForKeywordAndComment() {
        val code = "select * from t -- c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "sql",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "select"))
        assertTrue(containsStyledFragment(result, "from"))
        assertTrue(containsStyledFragment(result, "-- c"))
    }

    @Test
    fun htmlCode_addsStylesForCommentAndString() {
        val code = "<!-- c --> <div class='x'>hi</div>"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "html",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "<!-- c -->"))
        assertTrue(containsStyledFragment(result, "'x'"))
    }

    @Test
    fun jsonCode_addsStylesForStringAndLiteralKeyword() {
        val code = "{ \"a\": true, \"b\": null }"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "json",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "\"a\""))
        assertTrue(containsStyledFragment(result, "true"))
        assertTrue(containsStyledFragment(result, "null"))
    }

    @Test
    fun yamlCode_addsStylesForComment() {
        val code = "a: 1 # c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "yaml",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "# c"))
    }

    @Test
    fun unsupportedLanguage_returnsPlainAnnotatedString() {
        val code = "plain text"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "unknown",
            colors = lightColorScheme(),
        )

        assertTrue(result.spanStyles.isEmpty())
    }


    private fun hasStyledRangeCovering(
        annotatedString: AnnotatedString,
        start: Int,
        end: Int,
    ): Boolean {
        return annotatedString.spanStyles.any { range ->
            range.start <= start && range.end >= end
        }
    }

    private fun containsStyledFragment(
        annotatedString: AnnotatedString,
        fragment: String,
    ): Boolean {
        return annotatedString.spanStyles.any { range ->
            val start = range.start
            val end = range.end
            start in 0 until end && end <= annotatedString.length &&
                annotatedString.text.substring(start, end).contains(fragment)
        }
    }
}
