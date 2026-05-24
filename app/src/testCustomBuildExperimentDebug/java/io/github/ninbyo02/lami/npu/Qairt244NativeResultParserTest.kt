package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Test

class Qairt244NativeResultParserTest {
    @Test
    fun `single line output is preserved`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            marker=qairt244_editable_prompt_smoke_v1
            result=success
            output=hello
            selected_route=qairt244_sm8750_hidden_npu
            """.trimIndent(),
        )

        assertEquals("hello", parsed.output)
        assertEquals("hello", parsed.values["output"])
        assertEquals("qairt244_sm8750_hidden_npu", parsed.values["selected_route"])
    }

    @Test
    fun `multiline output is preserved until route diagnostics`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            result=success
            output=。

            お元気ですか。

            いつもお世話になっております。
            selected_route=qairt244_sm8750_hidden_npu
            max_output_tokens=128
            """.trimIndent(),
        )

        assertEquals(
            "。\n\nお元気ですか。\n\nいつもお世話になっております。",
            parsed.output,
        )
        assertEquals("128", parsed.values["max_output_tokens"])
    }

    @Test
    fun `output beginning with punctuation is not truncated to first line`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            result=success
            output=。
            次の行も本文です。
            route_type=standard_hidden_chat_screen
            """.trimIndent(),
        )

        assertEquals("。\n次の行も本文です。", parsed.output)
    }

    @Test
    fun `diagnostics lines after output are not included in body`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            result=success
            output=本文です。
            raw_native_output=本文です。
            adapter_output=本文です。
            adapter_output_length=4
            finish_reason=not_exposed_by_lower_level_entrypoint
            """.trimIndent(),
        )

        assertEquals("本文です。", parsed.output)
        assertEquals("本文です。", parsed.values["raw_native_output"])
        assertEquals("4", parsed.values["adapter_output_length"])
    }

    @Test
    fun `empty output remains empty and following diagnostics are parsed`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            result=success
            output=
            stop_reason=eos
            finish_reason=stop
            """.trimIndent(),
        )

        assertEquals("", parsed.output)
        assertEquals("eos", parsed.values["stop_reason"])
        assertEquals("stop", parsed.values["finish_reason"])
    }

    @Test
    fun `output followed by stop and finish reason keeps output separate`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            result=success
            output=回答本文。
            stop_reason=max_tokens
            finish_reason=length
            """.trimIndent(),
        )

        assertEquals("回答本文。", parsed.output)
        assertEquals("max_tokens", parsed.values["stop_reason"])
        assertEquals("length", parsed.values["finish_reason"])
    }

    @Test
    fun `output followed by result style diagnostics keeps output separate`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            output=回答本文。
            result=success
            decode_elapsed_ms=2670
            npu_backend=NPU
            """.trimIndent(),
        )

        assertEquals("回答本文。", parsed.output)
        assertEquals("success", parsed.values["result"])
        assertEquals("2670", parsed.values["decode_elapsed_ms"])
        assertEquals("NPU", parsed.values["npu_backend"])
    }

    @Test
    fun `output followed by unicode diagnostics keeps output separate`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            output=本文です。
            output_unicode_summary=utf16_length=4;code_point_count=4
            quality_classification=natural_japanese
            output_first_200_chars=本文です。
            output_last_200_chars=本文です。
            eos_detected=false
            """.trimIndent(),
        )

        assertEquals("本文です。", parsed.output)
        assertEquals("false", parsed.values["eos_detected"])
        assertEquals("natural_japanese", parsed.values["quality_classification"])
        assertEquals("本文です。", parsed.values["output_first_200_chars"])
    }

    @Test
    fun `output followed by template comparison diagnostics keeps output separate`() {
        val parsed = Qairt244NativeResultParser.parse(
            """
            output=回答本文。
            template_mode=chatml
            template_prefix_length=12
            template_suffix_length=34
            final_model_input_length=123
            raw_native_output_length=5
            displayed_assistant_text_length=5
            decode_elapsed_ms=42
            output_token_count=7
            replacement_char_count=0
            quality_classification=natural_japanese
            """.trimIndent(),
        )

        assertEquals("回答本文。", parsed.output)
        assertEquals("chatml", parsed.values["template_mode"])
        assertEquals("12", parsed.values["template_prefix_length"])
        assertEquals("34", parsed.values["template_suffix_length"])
        assertEquals("123", parsed.values["final_model_input_length"])
        assertEquals("natural_japanese", parsed.values["quality_classification"])
    }
}
