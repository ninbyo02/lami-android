package io.github.ninbyo02.lami.debug

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

internal object QairtElfInspector {
    fun inspect(file: File): ElfInspection {
        return inspect(bytes = file.readBytes(), source = file.absolutePath, size = file.length())
    }

    fun inspectApkEntry(apk: File, entryName: String): ElfInspection {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(entryName) ?: error("missing apk entry: $entryName")
            val bytes = zip.getInputStream(entry).use { input -> input.readBytes() }
            return inspect(bytes = bytes, source = "${apk.absolutePath}!/$entryName", size = entry.size)
        }
    }

    private fun inspect(bytes: ByteArray, source: String, size: Long): ElfInspection {
        require(bytes.size >= EI_NIDENT) { "too small for ELF header: ${bytes.size}" }
        require(bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
            "not an ELF file: $source"
        }

        val elfClass = bytes[4].toInt() and 0xff
        val data = bytes[5].toInt() and 0xff
        val is64 = elfClass == ELFCLASS64
        val order = when (data) {
            ELFDATA2LSB -> ByteOrder.LITTLE_ENDIAN
            ELFDATA2MSB -> ByteOrder.BIG_ENDIAN
            else -> error("unsupported ELF data encoding: $data")
        }
        val buffer = ByteBuffer.wrap(bytes).order(order)
        val machine = buffer.u16(18)
        val entry = if (is64) buffer.u64(24) else buffer.u32(24)
        val sectionHeaderOffset = if (is64) buffer.u64(40) else buffer.u32(32)
        val sectionHeaderEntrySize = if (is64) buffer.u16(58) else buffer.u16(46)
        val sectionHeaderCount = if (is64) buffer.u16(60) else buffer.u16(48)
        val sectionNameIndex = if (is64) buffer.u16(62) else buffer.u16(50)

        val sections = parseSections(
            bytes = bytes,
            buffer = buffer,
            is64 = is64,
            sectionHeaderOffset = sectionHeaderOffset,
            sectionHeaderEntrySize = sectionHeaderEntrySize,
            sectionHeaderCount = sectionHeaderCount,
            sectionNameIndex = sectionNameIndex,
        )
        val sectionNames = sections.map { it.name }
        val dynamicSymbols = parseDynamicSymbols(bytes = bytes, buffer = buffer, is64 = is64, sections = sections)
        val jniSymbols = dynamicSymbols
            .filter { it.startsWith("Java_") || it.contains("LiteRtLmJni") }
            .distinct()
            .sorted()
        val buildId = sections
            .firstNotNullOfOrNull { section ->
                if (section.type == SHT_NOTE) parseBuildIdNote(bytes = bytes, buffer = buffer, section = section) else null
            }

        return ElfInspection(
            source = source,
            size = size,
            elfClass = if (is64) "ELF64" else "ELF32",
            dataEncoding = if (order == ByteOrder.LITTLE_ENDIAN) "little-endian" else "big-endian",
            machine = machine,
            machineName = machineName(machine),
            entry = "0x${entry.toString(16)}",
            sectionCount = sections.size,
            sectionNames = sectionNames,
            hasSymtab = sections.any { it.type == SHT_SYMTAB },
            hasDynsym = sections.any { it.type == SHT_DYNSYM },
            buildId = buildId,
            dynamicSymbolCount = dynamicSymbols.size,
            jniSymbols = jniSymbols,
        )
    }

    private fun parseSections(
        bytes: ByteArray,
        buffer: ByteBuffer,
        is64: Boolean,
        sectionHeaderOffset: Long,
        sectionHeaderEntrySize: Int,
        sectionHeaderCount: Int,
        sectionNameIndex: Int,
    ): List<ElfSection> {
        if (sectionHeaderOffset <= 0 || sectionHeaderEntrySize <= 0 || sectionHeaderCount <= 0) return emptyList()
        val sectionHeaders = (0 until sectionHeaderCount).mapNotNull { index ->
            val offset = sectionHeaderOffset + index.toLong() * sectionHeaderEntrySize
            if (offset < 0 || offset + sectionHeaderEntrySize > bytes.size) return@mapNotNull null
            if (is64) {
                ElfSectionHeader(
                    nameOffset = buffer.u32(offset),
                    type = buffer.u32(offset + 4),
                    offset = buffer.u64(offset + 24),
                    size = buffer.u64(offset + 32),
                    link = buffer.u32(offset + 40).toInt(),
                    entrySize = buffer.u64(offset + 56),
                )
            } else {
                ElfSectionHeader(
                    nameOffset = buffer.u32(offset),
                    type = buffer.u32(offset + 4),
                    offset = buffer.u32(offset + 16),
                    size = buffer.u32(offset + 20),
                    link = buffer.u32(offset + 24).toInt(),
                    entrySize = buffer.u32(offset + 36),
                )
            }
        }
        val namesHeader = sectionHeaders.getOrNull(sectionNameIndex)
        return sectionHeaders.map { header ->
            ElfSection(
                name = readString(bytes, base = namesHeader?.offset ?: 0L, offset = header.nameOffset),
                type = header.type,
                offset = header.offset,
                size = header.size,
                link = header.link,
                entrySize = header.entrySize,
            )
        }
    }

    private fun parseDynamicSymbols(
        bytes: ByteArray,
        buffer: ByteBuffer,
        is64: Boolean,
        sections: List<ElfSection>,
    ): List<String> {
        val dynsym = sections.firstOrNull { it.type == SHT_DYNSYM } ?: return emptyList()
        val stringTable = sections.getOrNull(dynsym.link) ?: return emptyList()
        val entrySize = dynsym.entrySize.takeIf { it > 0 } ?: if (is64) 24L else 16L
        val count = (dynsym.size / entrySize).toInt()
        return (0 until count).mapNotNull { index ->
            val symbolOffset = dynsym.offset + index.toLong() * entrySize
            if (symbolOffset < 0 || symbolOffset + 4 > bytes.size) return@mapNotNull null
            val nameOffset = buffer.u32(symbolOffset)
            readString(bytes, base = stringTable.offset, offset = nameOffset).ifBlank { null }
        }
    }

    private fun parseBuildIdNote(bytes: ByteArray, buffer: ByteBuffer, section: ElfSection): String? {
        var offset = section.offset
        val end = section.offset + section.size
        while (offset + 12 <= end && offset + 12 <= bytes.size) {
            val nameSize = buffer.u32(offset)
            val descSize = buffer.u32(offset + 4)
            val type = buffer.u32(offset + 8)
            val nameOffset = offset + 12
            val descOffset = align4(nameOffset + nameSize)
            val nextOffset = align4(descOffset + descSize)
            if (nameOffset <= bytes.size && descOffset <= bytes.size && descOffset + descSize <= bytes.size) {
                val name = readRawString(bytes = bytes, offset = nameOffset, maxSize = nameSize.toInt())
                if (type == NT_GNU_BUILD_ID && name == "GNU") {
                    return bytes.copyOfRange(descOffset.toInt(), (descOffset + descSize).toInt())
                        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
                }
            }
            if (nextOffset <= offset) break
            offset = nextOffset
        }
        return null
    }

    private fun readString(bytes: ByteArray, base: Long, offset: Long): String {
        return readRawString(bytes = bytes, offset = base + offset, maxSize = bytes.size)
    }

    private fun readRawString(bytes: ByteArray, offset: Long, maxSize: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        val start = offset.toInt()
        val maxEnd = minOf(bytes.size, start + maxSize)
        var end = start
        while (end < maxEnd && bytes[end] != 0.toByte()) end++
        return bytes.copyOfRange(start, end).toString(Charsets.UTF_8)
    }

    private fun align4(value: Long): Long = (value + 3L) and 3L.inv()

    private fun ByteBuffer.u16(offset: Int): Int = getShort(offset).toInt() and 0xffff
    private fun ByteBuffer.u32(offset: Long): Long = getInt(offset.toInt()).toLong() and 0xffffffffL
    private fun ByteBuffer.u64(offset: Long): Long = getLong(offset.toInt())

    private fun machineName(machine: Int): String {
        return when (machine) {
            0x28 -> "ARM"
            0x3e -> "x86-64"
            0xb7 -> "AArch64"
            else -> "unknown"
        }
    }

    data class ElfInspection(
        val source: String,
        val size: Long,
        val elfClass: String,
        val dataEncoding: String,
        val machine: Int,
        val machineName: String,
        val entry: String,
        val sectionCount: Int,
        val sectionNames: List<String>,
        val hasSymtab: Boolean,
        val hasDynsym: Boolean,
        val buildId: String?,
        val dynamicSymbolCount: Int,
        val jniSymbols: List<String>,
    ) {
        val stripped: Boolean
            get() = !hasSymtab
    }

    private data class ElfSectionHeader(
        val nameOffset: Long,
        val type: Long,
        val offset: Long,
        val size: Long,
        val link: Int,
        val entrySize: Long,
    )

    private data class ElfSection(
        val name: String,
        val type: Long,
        val offset: Long,
        val size: Long,
        val link: Int,
        val entrySize: Long,
    )

    private const val EI_NIDENT = 16
    private const val ELFCLASS64 = 2
    private const val ELFDATA2LSB = 1
    private const val ELFDATA2MSB = 2
    private const val SHT_SYMTAB = 2L
    private const val SHT_DYNSYM = 11L
    private const val SHT_NOTE = 7L
    private const val NT_GNU_BUILD_ID = 3L
}
