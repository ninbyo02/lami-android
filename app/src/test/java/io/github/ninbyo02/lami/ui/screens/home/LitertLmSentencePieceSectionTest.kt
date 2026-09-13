package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class LitertLmSentencePieceSectionTest {
    private fun header(): ByteArray = ByteArray(128).also {
        val b = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0, 16)
        b.putShort(8, 8); b.putShort(10, 8); b.putShort(14, 4)
        b.putInt(16, 8); b.putInt(20, 20)
        b.putShort(32, 6); b.putShort(34, 8); b.putShort(36, 4)
        b.putInt(40, 8); b.putInt(44, 12)
        b.putInt(56, 1); b.putInt(60, 28)
        b.putShort(72, 12); b.putShort(74, 32)
        b.putShort(78, 8); b.putShort(80, 16); b.putShort(82, 24)
        b.putInt(88, 16); b.putLong(96, 160); b.putLong(104, 168); b.put(112, 4)
    }
    private fun reject(bytes: ByteArray, end: Long = bytes.size + 32L, size: Long = 168) {
        try { LitertLmSentencePieceSection.locate(bytes, end, size); fail("invalid header accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun readsOnlyTokenizerSectionFromContainer() {
        val file = File.createTempFile("tokenizer", ".litertlm")
        try {
            val prefix = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            prefix.put("LITERTLM".toByteArray()); prefix.putInt(1); prefix.putInt(5); prefix.putInt(0)
            prefix.putInt(0); prefix.putLong(160)
            val payload = byteArrayOf(1,2,3,4,5,6,7,8)
            file.writeBytes(prefix.array() + header() + payload)
            assertArrayEquals(payload, LitertLmSentencePieceSection.read(file))
            val bad = file.readBytes(); bad[12] = 6; file.writeBytes(bad)
            try { LitertLmSentencePieceSection.read(file); fail("unknown version") } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
    @Test fun supportsSignedSharedVtableOffset() {
        val h = header(); val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        h.copyInto(h, 120, 8, 16); b.putInt(16, -104)
        assertEquals(LitertLmSentencePieceSection.Section(160, 8), LitertLmSentencePieceSection.locate(h, 160, 168))
    }
    @Test fun rejectsTruncatedAndOverflowingHeaderReferences() {
        for (size in 0..119) reject(header().copyOf(size))
        for (offset in listOf(0, 20, 44, 60)) {
            val h = header(); ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1); reject(h)
        }
        val h = header(); ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).putInt(88, Int.MIN_VALUE); reject(h)
    }
    @Test fun rejectsInvalidTableSizesFieldsAndSectionCounts() {
        for ((offset, value) in listOf(72 to 3, 74 to 2, 78 to 2, 80 to 31)) {
            val h = header(); ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, value.toShort()); reject(h)
        }
        for (count in listOf(0, 4097, -1)) {
            val h = header(); ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).putInt(56, count); reject(h)
        }
    }
    @Test fun rejectsMissingDuplicateAndOutOfFileSections() {
        val missing = header(); missing[112] = 6; reject(missing)
        val duplicate = header(); val d = ByteBuffer.wrap(duplicate).order(ByteOrder.LITTLE_ENDIAN)
        d.putInt(56, 2); d.putInt(64, 24); reject(duplicate)
        for ((begin, end) in listOf(-1L to 168L, 159L to 168L, 160L to 159L, 160L to Long.MAX_VALUE, 160L to 160L)) {
            val h = header(); val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            b.putLong(96, begin); b.putLong(104, end); reject(h)
        }
    }
    @Test fun rejectsOversizedTokenizerBeforeAllocation() {
        val h = header(); val end = 160L + LitertLmSentencePieceSection.MAX_TOKENIZER_BYTES + 1
        ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).putLong(104, end)
        reject(h, 160, end)
    }
}
