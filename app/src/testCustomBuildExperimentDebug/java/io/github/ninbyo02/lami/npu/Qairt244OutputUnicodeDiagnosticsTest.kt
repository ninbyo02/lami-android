package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244OutputUnicodeDiagnosticsTest {
    @Test
    fun `white circles are reported as real U3007 code points`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields("〇〇〇〇").toMap()

        assertEquals("false", fields["output_contains_replacement_chars"])
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("classification=white_circle_code_points"))
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("white_circle_u3007_count=4"))
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("first_code_points=U+3007 U+3007 U+3007 U+3007"))
    }

    @Test
    fun `replacement characters are reported separately from white circles`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields("����").toMap()

        assertEquals("true", fields["output_contains_replacement_chars"])
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("classification=contains_unicode_replacement_char"))
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("replacement_char_count=4"))
    }

    @Test
    fun `single question mark and eos stop are diagnosable`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields(
            output = "？",
            values = mapOf(
                "finish_reason" to "stop",
                "stop_reason" to "eos",
                "output_token_count" to "1",
            ),
        ).toMap()

        assertEquals("1", fields["output_token_count"])
        assertEquals("true", fields["eos_detected"])
        assertEquals("？", fields["output_first_200_chars"])
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("classification=single_question_mark_output"))
        assertTrue(fields["output_unicode_summary"].orEmpty().contains("question_mark_count=1"))
    }
}
