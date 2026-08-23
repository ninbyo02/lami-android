package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalInferenceResponseQualityTest {
    @Test
    fun `Japanese prose with Arabic and Hangul contamination is rejected`() {
        assertEquals(
            "unexpected_script:ARABIC,HANGUL",
            rejectionReason(
                userPrompt = "pythonでテトリスを作って",
                response = "Pythonでテトリスを作成 کردنことは、非常にやりがいのあるプロジェクト입니다！",
            ),
        )
    }

    @Test
    fun `observed Japanese response with a repeated Korean tail is rejected`() {
        val response = buildString {
            append("いかがお過ごしでしょうか。皆さんの毎日が元気と喜びで満たされることを願っています。")
            append("\n\n何かお手伝いできることがあれば、")
            repeat(48) { append("든지") }
        }

        assertEquals(
            "degenerate_repetition",
            rejectionReason(
                userPrompt = "こんにちは",
                response = response,
            ),
        )
    }

    @Test
    fun `short natural emphasis is not classified as degenerate repetition`() {
        assertNull(
            rejectionReason(
                userPrompt = "日本語で挨拶して",
                response = "とてもとても嬉しいです。こんにちは。",
            ),
        )
    }

    @Test
    fun `repetition inside a closed code fence is excluded from prose quality checks`() {
        assertNull(
            rejectionReason(
                userPrompt = "Unicodeのテストデータをコードとして示して",
                response = """
                    次の文字列をテストデータとして使えます。
                    ```text
                    ${"든지".repeat(48)}
                    ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `Japanese technical prose with Latin terms remains accepted`() {
        assertNull(
            rejectionReason(
                userPrompt = "pythonでテトリスを作って",
                response = "PythonとPygameを使って、テトリスの基本構造を作成します。",
            ),
        )
    }

    @Test
    fun `foreign scripts inside fenced code do not reject the response`() {
        assertNull(
            rejectionReason(
                userPrompt = "PythonでUnicode文字列を表示する例を作って",
                response = """
                    次のように文字列を指定できます。
                    ```python
                    print("입니다")
                    print("کردن")
                    ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `explicit Korean request allows Hangul prose`() {
        assertNull(
            rejectionReason(
                userPrompt = "韓国語で短く挨拶して",
                response = "안녕하세요。",
            ),
        )
    }

    @Test
    fun `explicit Arabic request allows Arabic prose`() {
        assertNull(
            rejectionReason(
                userPrompt = "アラビア語で短く挨拶して",
                response = "مرحبا。",
            ),
        )
    }

    @Test
    fun `negative Korean request does not permit Hangul contamination`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "韓国語ではなく日本語だけで答えて",
                response = "日本語で回答입니다。",
            ),
        )
    }

    @Test
    fun `quoted Hangul in the prompt does not permit contaminated prose`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "「입니다」は出力しないで、日本語だけで答えて",
                response = "回答입니다。",
            ),
        )
    }

    @Test
    fun `legitimate parenthesized local names remain accepted`() {
        assertNull(
            rejectionReason(
                userPrompt = "韓国とアブダビの都市名を現地表記付きで教えて",
                response = "ソウル（서울）とアブダビ（أبو ظبي）です。",
            ),
        )
    }

    @Test
    fun `kanji only prompt still rejects directly joined Hangul`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "要約",
                response = "概要입니다。",
            ),
        )
    }

    @Test
    fun `single unexpected script code point joined to Japanese is rejected`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で答えて",
                response = "回答한。",
            ),
        )
    }

    @Test
    fun `closed multi backtick inline code is excluded`() {
        assertNull(
            rejectionReason(
                userPrompt = "Unicode文字列の例を教えて",
                response = "例は ``回答입니다`` です。",
            ),
        )
    }

    @Test
    fun `four backtick fence is not closed by a triple backtick line`() {
        assertNull(
            rejectionReason(
                userPrompt = "コード例を教えて",
                response = """
                    ````text
                    回答입니다
                    ```
                    ````
                    日本語の説明です。
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `unclosed fence cannot hide contamination`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で説明して",
                response = """
                    説明を始めます。
                    ```text
                    回答입니다
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `mismatched fence marker cannot hide contamination`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で説明して",
                response = """
                    ```text
                    回答입니다
                    ~~~
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `four space indented fence cannot hide contamination`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で説明して",
                response = """
                        ```text
                    回答입니다
                        ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `backtick in backtick fence info string makes the fence invalid`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で説明して",
                response = """
                    ```text`
                    回答입니다
                    ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `backslash does not escape an inline code closing delimiter`() {
        assertEquals(
            "unexpected_script:HANGUL",
            rejectionReason(
                userPrompt = "日本語で説明して",
                response = "`code\\`回答입니다`",
            ),
        )
    }

    @Test
    fun `closed multiline inline code is excluded`() {
        assertNull(
            rejectionReason(
                userPrompt = "Unicode文字列の例を教えて",
                response = """
                    例:
                    `回答입니다
                    続き`
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `GPU contamination is rejected and CPU Japanese answer is selected`() = runBlocking {
        val calls = mutableListOf<String>()
        val chain = runInferenceBackendChain(
            attempts = listOf(
                InferenceBackendChainAttempt("GPU") {
                    calls += "GPU"
                    "作成 کردنことはプロジェクト입니다。"
                },
                InferenceBackendChainAttempt("CPU") {
                    calls += "CPU"
                    "PythonとPygameで段階的に実装できます。"
                },
            ),
            shouldFallback = { response ->
                rejectionReason("pythonでテトリスを作って", response) != null
            },
        )

        assertEquals(listOf("GPU", "CPU"), calls)
        assertEquals("CPU", chain.successfulBackend)
        assertEquals("PythonとPygameで段階的に実装できます。", chain.result)
    }

    @Test
    fun `GPU repeated Korean tail is rejected and CPU Japanese answer is selected`() = runBlocking {
        val calls = mutableListOf<String>()
        val badGpuResponse = "日本語の前置きです。" + "든지".repeat(48)
        val chain = runInferenceBackendChain(
            attempts = listOf(
                InferenceBackendChainAttempt("GPU") {
                    calls += "GPU"
                    badGpuResponse
                },
                InferenceBackendChainAttempt("CPU") {
                    calls += "CPU"
                    "こんにちは。今日は何をお手伝いしましょうか。"
                },
            ),
            shouldFallback = { response ->
                rejectionReason("こんにちは", response) != null
            },
        )

        assertEquals(listOf("GPU", "CPU"), calls)
        assertEquals("CPU", chain.successfulBackend)
        assertEquals("こんにちは。今日は何をお手伝いしましょうか。", chain.result)
    }

    @Test
    fun `GPU and CPU contamination leave no accepted response`() = runBlocking {
        val chain = runInferenceBackendChain(
            attempts = listOf(
                InferenceBackendChainAttempt("GPU") { "回答입니다。" },
                InferenceBackendChainAttempt("CPU") { "説明 کردنことです。" },
            ),
            shouldFallback = { response ->
                rejectionReason("日本語で答えて", response) != null
            },
        )

        assertNull(chain.successfulBackend)
        assertNull(
            acceptedResponse(
                successfulBackend = chain.successfulBackend,
                response = chain.result,
            ),
        )
    }

    @Test
    fun `successful backend is revalidated before its response is accepted`() {
        assertNull(
            acceptedResponse(
                userPrompt = "こんにちは",
                successfulBackend = "GPU",
                response = "日本語の前置きです。" + "든지".repeat(48),
            ),
        )
    }

    @Test
    fun `rejected nonblank result is never accepted without a successful backend`() {
        assertNull(
            acceptedResponse(
                successfulBackend = null,
                response = "作成 کردنことはプロジェクト입니다。",
            ),
        )
        assertEquals(
            "安全な回答",
            acceptedResponse(successfulBackend = "CPU", response = " 安全な回答 "),
        )
    }

    private fun rejectionReason(userPrompt: String, response: String?): String? =
        localInferenceResponseRejectionReason(userPrompt, response)

    private fun acceptedResponse(
        userPrompt: String = "日本語で答えて",
        successfulBackend: String?,
        response: String?,
    ): String? =
        acceptedLocalInferenceResponse(
            userPrompt = userPrompt,
            successfulBackend = successfulBackend,
            response = response,
        )
}
