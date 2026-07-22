package io.github.ninbyo02.lami.gpu

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugTokenBenchmarkCsvParserTest {
    @Test
    fun `quoted prompt newline stays inside one CSV record`() {
        val csv = "\"prompt\",\"status\"\n\"line one\nline two\",\"success\"\n"

        val records = DebugTokenBenchmarkCsvParser.records(csv)

        assertEquals(2, records.size)
        assertEquals(
            listOf("line one\nline two", "success"),
            DebugTokenBenchmarkCsvParser.cells(records[1]),
        )
    }

    @Test
    fun `CRLF boundaries preserve empty records and quoted CRLF`() {
        val csv = "header\r\n\r\n\"line one\r\nline two\",tail\r\n"

        val records = DebugTokenBenchmarkCsvParser.records(csv)

        assertEquals(listOf("header", "", "\"line one\r\nline two\",tail"), records)
        assertEquals(listOf("line one\r\nline two", "tail"), DebugTokenBenchmarkCsvParser.cells(records[2]))
    }

    @Test
    fun `escaped quotes empty cells and missing terminal newline are preserved`() {
        val records = DebugTokenBenchmarkCsvParser.records("\"a\"\"b\",,\"\"")

        assertEquals(1, records.size)
        assertEquals(listOf("a\"b", "", ""), DebugTokenBenchmarkCsvParser.cells(records.single()))
    }
}
