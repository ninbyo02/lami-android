package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded reader for the tokenizer field in LiteRT-LM schema 1.5.0.
 * No TFLite model section is read or mapped. Unknown versions are rejected.
 */
internal object LitertLmSentencePieceSection {
    const val MAX_HEADER_BYTES = 1024 * 1024
    const val MAX_TOKENIZER_BYTES = 16 * 1024 * 1024
    private const val MAX_SECTIONS = 4096

    data class Section(val offset: Long, val size: Int)

    fun read(file: File): ByteArray = RandomAccessFile(file, "r").use { input ->
        val fileSize = input.length()
        require(fileSize >= 32) { "truncated-prefix" }
        val prefix = ByteArray(32).also(input::readFully)
        require(prefix.copyOfRange(0, 8).contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII))) { "invalid-magic" }
        val view = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN)
        require(view.getInt(8) == 1 && view.getInt(12) == 5 && view.getInt(16) == 0) { "unsupported-version" }
        val headerEnd = view.getLong(24)
        require(headerEnd in 33..minOf(fileSize, 32L + MAX_HEADER_BYTES)) { "invalid-header-size" }
        val header = ByteArray((headerEnd - 32).toInt()).also(input::readFully)
        val section = locate(header, headerEnd, fileSize)
        input.seek(section.offset)
        ByteArray(section.size).also(input::readFully)
    }

    // FlatBuffers field ordinals follow pinned LiteRTLMMetaData schema v0.11.0.
    fun locate(header: ByteArray, headerEnd: Long, fileSize: Long): Section {
        require(header.isNotEmpty() && header.size <= MAX_HEADER_BYTES)
        require(headerEnd == header.size.toLong() + 32 && fileSize >= headerEnd)
        val reader = HeaderReader(header)
        val root = reader.reference(0)
        val metadata = reader.reference(reader.requiredField(root, 1, 4))
        val vector = reader.reference(reader.requiredField(metadata, 0, 4))
        val count = reader.uint(vector)
        require(count in 1..MAX_SECTIONS.toLong()) { "invalid-section-count" }
        reader.range(vector.toLong() + 4, count * 4)
        var selected: Section? = null
        val ranges = mutableListOf<Pair<Long, Long>>()
        repeat(count.toInt()) { index ->
            val table = reader.reference(vector + 4 + index * 4)
            val begin = reader.longField(table, 1)
            val end = reader.longField(table, 2)
            val typeOffset = reader.field(table, 3, 1)
            val type = if (typeOffset == null) 0 else reader.byte(typeOffset)
            require(begin >= headerEnd && end >= begin && end <= fileSize) { "invalid-section-range" }
            ranges += begin to end
            if (type == 4) {
                require(selected == null) { "ambiguous-tokenizer" }
                require(end - begin in 1..MAX_TOKENIZER_BYTES.toLong()) { "invalid-tokenizer-size" }
                selected = Section(begin, (end - begin).toInt())
            }
        }
        ranges.sortedBy { it.first }.zipWithNext().forEach { (left, right) ->
            require(left.second <= right.first) { "overlapping-sections" }
        }
        return requireNotNull(selected) { "sentencepiece-section-missing" }
    }

    private class HeaderReader(private val bytes: ByteArray) {
        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun range(offset: Long, size: Long) {
            require(offset >= 0 && size >= 0 && offset <= bytes.size.toLong() - size) { "invalid-header-range" }
        }
        fun uint(offset: Int): Long { range(offset.toLong(), 4); return buffer.getInt(offset).toLong() and 0xffffffffL }
        fun byte(offset: Int): Int { range(offset.toLong(), 1); return bytes[offset].toInt() and 255 }
        private fun ushort(offset: Int): Int { range(offset.toLong(), 2); return buffer.getShort(offset).toInt() and 65535 }
        fun reference(offset: Int): Int {
            val relative = uint(offset)
            require(relative >= 4) { "invalid-reference" }
            val target = offset.toLong() + relative
            range(target, 4)
            return target.toInt()
        }
        fun field(table: Int, ordinal: Int, width: Int): Int? {
            range(table.toLong(), 4)
            // Signed offset: shared vtables may appear after the table.
            val vtable = table.toLong() - buffer.getInt(table).toLong()
            range(vtable, 4)
            val vt = vtable.toInt()
            val vtableSize = ushort(vt)
            val objectSize = ushort(vt + 2)
            require(vtableSize >= 4 && vtableSize % 2 == 0 && objectSize >= 4) { "invalid-table" }
            range(vtable, vtableSize.toLong()); range(table.toLong(), objectSize.toLong())
            val entry = 4 + ordinal * 2
            if (entry + 2 > vtableSize) return null
            val relative = ushort(vt + entry)
            if (relative == 0) return null
            require(relative >= 4 && relative <= objectSize - width) { "invalid-field" }
            return table + relative
        }
        fun requiredField(table: Int, ordinal: Int, width: Int): Int =
            requireNotNull(field(table, ordinal, width)) { "required-field-missing" }
        fun longField(table: Int, ordinal: Int): Long =
            field(table, ordinal, 8)?.let { buffer.getLong(it) } ?: 0L
    }
}
