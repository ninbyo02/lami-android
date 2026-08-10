package io.github.ninbyo02.lami.ui.screens.spriteeditor

import android.graphics.Bitmap
import java.util.Collections

private const val ADAPTIVE_MAX_RGB_COLORS = 256
private const val ADAPTIVE_EXACT_HISTOGRAM_LIMIT = 32_768
private const val ADAPTIVE_FALLBACK_BINS = 32 * 32 * 32
private const val ADAPTIVE_LLOYD_REFINEMENTS = 4
private const val ADAPTIVE_CANCEL_ENTRY_INTERVAL = 512
private const val ADAPTIVE_CANCEL_PIXEL_INTERVAL = 1024
private const val OKLAB_Q14 = 16_384.0

fun reduceToAdaptivePalette(
    src: Bitmap,
    maxRgbColors: Int = ADAPTIVE_MAX_RGB_COLORS,
    reuseRgbAnchors: List<Int>? = null,
    shouldCancel: (checkpoint: Int) -> Boolean = { false },
): PaletteBitmapResult {
    if (src.isRecycled) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.RECYCLED,
        )
    }
    if (maxRgbColors !in 1..ADAPTIVE_MAX_RGB_COLORS) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
        )
    }
    if (src.config == Bitmap.Config.HARDWARE) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
        )
    }

    val width = runCatching { src.width }.getOrElse {
        return PaletteBitmapResult(src, changed = false, rejectionReason = PaletteBitmapRejectionReason.READ_FAILED)
    }
    val height = runCatching { src.height }.getOrElse {
        return PaletteBitmapResult(src, changed = false, rejectionReason = PaletteBitmapRejectionReason.READ_FAILED)
    }
    if (width <= 0 || height <= 0) {
        return PaletteBitmapResult(src, changed = false, rgbAnchors = immutableAnchorsOrEmpty(reuseRgbAnchors))
    }
    if (width.toLong() * height.toLong() > SPRITE_BITMAP_OPS_MAX_PIXELS) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.TOO_LARGE,
        )
    }

    val anchorsForReuse = reuseRgbAnchors?.let { validateAnchors(it, maxRgbColors) }
    if (reuseRgbAnchors != null && anchorsForReuse == null) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
        )
    }

    var checkpoint = 0
    fun cancelled(): Boolean = shouldCancel(checkpoint++)
    fun cancelledResult(): PaletteBitmapResult = PaletteBitmapResult(
        src,
        changed = false,
        rejectionReason = PaletteBitmapRejectionReason.CANCELLED,
        cancelled = true,
        rgbAnchors = anchorsForReuse ?: emptyList(),
    )

    val readable = readableAdaptiveArgb8888Bitmap(src) ?: return PaletteBitmapResult(
        src,
        changed = false,
        rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
    )
    val safeSrc = readable.bitmap
    val isPremultiplied = safeSrc.isPremultiplied

    if (anchorsForReuse != null) {
        val reuseResult = mapAdaptiveRows(
            src = src,
            safeSrc = safeSrc,
            readable = readable,
            width = width,
            height = height,
            isPremultiplied = isPremultiplied,
            anchors = anchorsForReuse,
            entries = null,
            exactMapping = false,
            shouldCancel = ::cancelled,
            cancelledResult = ::cancelledResult,
        )
        return reuseResult
    }

    val histogram = runCatching { AdaptiveHistogram() }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    val rowPixels = runCatching { IntArray(width) }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    for (y in 0 until height) {
        if (cancelled()) {
            readable.recycleIfNew()
            return cancelledResult()
        }
        if (!runCatching { safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1) }.isSuccess) {
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.READ_FAILED,
            )
        }
        for (x in 0 until width) {
            if (x != 0 && x % ADAPTIVE_CANCEL_PIXEL_INTERVAL == 0 && cancelled()) {
                readable.recycleIfNew()
                return cancelledResult()
            }
            val pixel = rowPixels[x]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha != 0) {
                histogram.add(pixel and 0x00FFFFFF, alpha)
            }
        }
    }

    if (histogram.visibleCount == 0 || histogram.exactUnique in 1..maxRgbColors && !histogram.exactOverflowed) {
        readable.recycleIfNew()
        return PaletteBitmapResult(src, changed = false)
    }
    if (cancelled()) {
        readable.recycleIfNew()
        return cancelledResult()
    }

    val entries = runCatching { histogram.entries(::cancelled) }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    } ?: run {
        readable.recycleIfNew()
        return cancelledResult()
    }
    if (entries.isEmpty()) {
        readable.recycleIfNew()
        return PaletteBitmapResult(src, changed = false)
    }
    if (cancelled()) {
        readable.recycleIfNew()
        return cancelledResult()
    }

    val generatedAnchors = runCatching {
        generateAdaptiveAnchors(entries, maxRgbColors, ::cancelled)
    }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    if (generatedAnchors == null) {
        readable.recycleIfNew()
        return cancelledResult()
    }
    if (cancelled()) {
        readable.recycleIfNew()
        return cancelledResult()
    }

    return mapAdaptiveRows(
        src = src,
        safeSrc = safeSrc,
        readable = readable,
        width = width,
        height = height,
        isPremultiplied = isPremultiplied,
        anchors = generatedAnchors,
        entries = entries,
        exactMapping = !histogram.exactOverflowed,
        shouldCancel = ::cancelled,
        cancelledResult = ::cancelledResult,
    )
}

private fun immutableAnchorsOrEmpty(anchors: List<Int>?): List<Int> {
    return anchors?.let { validateAnchors(it, ADAPTIVE_MAX_RGB_COLORS) } ?: emptyList()
}

private fun validateAnchors(anchors: List<Int>, maxRgbColors: Int): List<Int>? {
    if (anchors.size > maxRgbColors) return null
    val seen = HashSet<Int>(anchors.size)
    val clean = ArrayList<Int>(anchors.size)
    for (anchor in anchors) {
        if ((anchor ushr 24) != 0xFF) return null
        val rgb = anchor and 0x00FFFFFF
        if (!seen.add(rgb)) return null
        clean.add(0xFF000000.toInt() or rgb)
    }
    return Collections.unmodifiableList(clean)
}

private data class AdaptiveReadableBitmap(
    val bitmap: Bitmap,
    val isNew: Boolean,
) {
    fun recycleIfNew() {
        if (isNew && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private fun readableAdaptiveArgb8888Bitmap(src: Bitmap): AdaptiveReadableBitmap? {
    if (src.config == Bitmap.Config.ARGB_8888) {
        return AdaptiveReadableBitmap(src, isNew = false)
    }
    val copy = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull() ?: return null
    return AdaptiveReadableBitmap(copy, isNew = true)
}

private fun mapAdaptiveRows(
    src: Bitmap,
    safeSrc: Bitmap,
    readable: AdaptiveReadableBitmap,
    width: Int,
    height: Int,
    isPremultiplied: Boolean,
    anchors: List<Int>,
    entries: Array<AdaptiveColorEntry>?,
    exactMapping: Boolean,
    shouldCancel: () -> Boolean,
    cancelledResult: () -> PaletteBitmapResult,
): PaletteBitmapResult {
    if (shouldCancel()) {
        readable.recycleIfNew()
        return cancelledResult()
    }
    if (anchors.isEmpty()) {
        return mapAdaptiveRowsWithEmptyAnchors(
            src = src,
            safeSrc = safeSrc,
            readable = readable,
            width = width,
            height = height,
            shouldCancel = shouldCancel,
            cancelledResult = cancelledResult,
            anchors = anchors,
        )
    }

    val rowPixels = runCatching { IntArray(width) }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
            rgbAnchors = anchors,
        )
    }
    val mapper = runCatching {
        AdaptivePaletteMapper.create(
            anchors = anchors,
            entries = entries,
            exactMapping = exactMapping,
            shouldCancel = shouldCancel,
        )
    }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
            rgbAnchors = anchors,
        )
    } ?: run {
        readable.recycleIfNew()
        return cancelledResult()
    }
    if (shouldCancel()) {
        readable.recycleIfNew()
        return cancelledResult()
    }

    var createdOutput: Bitmap? = null
    val output = runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            createdOutput = it
            it.setPremultiplied(isPremultiplied)
        }
    }.getOrElse {
        createdOutput?.recycle()
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
            rgbAnchors = anchors,
        )
    }

    var changed = false
    for (y in 0 until height) {
        if (shouldCancel()) {
            output.recycle()
            readable.recycleIfNew()
            return cancelledResult()
        }
        if (!runCatching { safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1) }.isSuccess) {
            output.recycle()
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.READ_FAILED,
                rgbAnchors = anchors,
            )
        }
        for (x in 0 until width) {
            if (x != 0 && x % ADAPTIVE_CANCEL_PIXEL_INTERVAL == 0 && shouldCancel()) {
                output.recycle()
                readable.recycleIfNew()
                return cancelledResult()
            }
            val pixel = rowPixels[x]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha == 0 || mapper.isNoOp(pixel, alpha, isPremultiplied)) {
                continue
            }
            val nearest = mapper.map(pixel and 0x00FFFFFF)
            val mapped = (alpha shl 24) or (nearest and 0x00FFFFFF)
            if (pixel != mapped) {
                rowPixels[x] = mapped
                changed = true
            }
        }
        if (!runCatching { output.setPixels(rowPixels, 0, width, 0, y, width, 1) }.isSuccess) {
            output.recycle()
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.WRITE_FAILED,
                rgbAnchors = anchors,
            )
        }
    }

    readable.recycleIfNew()
    if (!changed) {
        output.recycle()
        return PaletteBitmapResult(src, changed = false, rgbAnchors = anchors)
    }
    return PaletteBitmapResult(output, changed = true, rgbAnchors = anchors)
}

private fun mapAdaptiveRowsWithEmptyAnchors(
    src: Bitmap,
    safeSrc: Bitmap,
    readable: AdaptiveReadableBitmap,
    width: Int,
    height: Int,
    shouldCancel: () -> Boolean,
    cancelledResult: () -> PaletteBitmapResult,
    anchors: List<Int>,
): PaletteBitmapResult {
    val rowPixels = runCatching { IntArray(width) }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
            rgbAnchors = anchors,
        )
    }
    for (y in 0 until height) {
        if (shouldCancel()) {
            readable.recycleIfNew()
            return cancelledResult()
        }
        if (!runCatching { safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1) }.isSuccess) {
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.READ_FAILED,
                rgbAnchors = anchors,
            )
        }
        for (x in 0 until width) {
            if (x != 0 && x % ADAPTIVE_CANCEL_PIXEL_INTERVAL == 0 && shouldCancel()) {
                readable.recycleIfNew()
                return cancelledResult()
            }
            if (((rowPixels[x] ushr 24) and 0xFF) != 0) {
                readable.recycleIfNew()
                return PaletteBitmapResult(
                    src,
                    changed = false,
                    rejectionReason = PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
                    rgbAnchors = anchors,
                )
            }
        }
    }
    readable.recycleIfNew()
    return PaletteBitmapResult(src, changed = false, rgbAnchors = anchors)
}

private class AdaptivePaletteMapper private constructor(
    private val exactMap: ExactRgbMap?,
    private val binMap: IntArray?,
    private val anchorSet: ExactRgbSet,
    private val physicalSet: ExactRgbSet,
) {
    fun map(rgb: Int): Int {
        val exact = exactMap
        if (exact != null) return exact.get(rgb)
        val bins = binMap ?: return 0xFF000000.toInt() or rgb
        return bins[fiveBitBin(rgb)]
    }

    fun isNoOp(pixel: Int, alpha: Int, isPremultiplied: Boolean): Boolean {
        val rgb = pixel and 0x00FFFFFF
        if (!isPremultiplied) return anchorSet.contains(rgb)
        return physicalSet.contains(physicalPremultipliedKeyAdaptive(alpha, rgb))
    }

    companion object {
        fun create(
            anchors: List<Int>,
            entries: Array<AdaptiveColorEntry>?,
            exactMapping: Boolean,
            shouldCancel: () -> Boolean,
        ): AdaptivePaletteMapper? {
            val anchorRgbs = IntArray(anchors.size) { anchors[it] and 0x00FFFFFF }
            val anchorLabs = Array(anchors.size) { rgbToOklabQ14(anchorRgbs[it]) }
            val anchorSet = ExactRgbSet((anchorRgbs.size * 4).coerceAtLeast(16))
            for (i in anchorRgbs.indices) {
                if (i != 0 && i % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
                anchorSet.add(anchorRgbs[i])
            }

            val physicalSet = ExactRgbSet((anchorRgbs.size * 256 * 2).coerceAtLeast(512))
            for (alpha in 0..255) {
                for (i in anchorRgbs.indices) {
                    val ordinal = alpha * anchorRgbs.size + i
                    if (ordinal != 0 && ordinal % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
                    physicalSet.add(physicalPremultipliedKeyAdaptive(alpha, anchorRgbs[i]))
                }
            }

            if (entries != null && exactMapping) {
                val map = ExactRgbMap((entries.size * 4).coerceAtLeast(16))
                for (i in entries.indices) {
                    if (i != 0 && i % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
                    val rgb = entries[i].rgb
                    map.put(rgb, nearestAnchor(rgb, rgbToOklabQ14(rgb), anchorRgbs, anchorLabs))
                }
                return AdaptivePaletteMapper(map, null, anchorSet, physicalSet)
            }

            val bins = IntArray(ADAPTIVE_FALLBACK_BINS)
            if (entries == null) {
                for (bin in bins.indices) {
                    if (bin != 0 && bin % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
                    val rgb = binCenterRgb(bin)
                    bins[bin] = nearestAnchor(rgb, rgbToOklabQ14(rgb), anchorRgbs, anchorLabs)
                }
            } else {
                for (i in entries.indices) {
                    if (i != 0 && i % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
                    val rgb = entries[i].rgb
                    bins[entries[i].bin] = nearestAnchor(rgb, entries[i].lab, anchorRgbs, anchorLabs)
                }
            }
            return AdaptivePaletteMapper(null, bins, anchorSet, physicalSet)
        }

        private fun nearestAnchor(
            rgb: Int,
            lab: OklabQ14,
            anchorRgbs: IntArray,
            anchorLabs: Array<OklabQ14>,
        ): Int {
            var bestIndex = 0
            var bestDistance = Long.MAX_VALUE
            for (i in anchorRgbs.indices) {
                val distance = oklabDistanceSquaredQ14(lab, anchorLabs[i])
                if (
                    distance < bestDistance ||
                    distance == bestDistance && anchorRgbs[i] < anchorRgbs[bestIndex]
                ) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            return 0xFF000000.toInt() or anchorRgbs[bestIndex]
        }
    }
}

private class ExactRgbMap(capacityHint: Int) {
    private val keys = IntArray(tableSizeFor(capacityHint))
    private val used = BooleanArray(keys.size)
    private val values = IntArray(keys.size)

    fun put(key: Int, value: Int) {
        var slot = mixRgb(key) and (keys.size - 1)
        while (true) {
            if (!used[slot] || keys[slot] == key) {
                used[slot] = true
                keys[slot] = key
                values[slot] = value
                return
            }
            slot = (slot + 1) and (keys.size - 1)
        }
    }

    fun get(key: Int): Int {
        var slot = mixRgb(key) and (keys.size - 1)
        while (true) {
            if (!used[slot]) return 0xFF000000.toInt() or key
            if (keys[slot] == key) return values[slot]
            slot = (slot + 1) and (keys.size - 1)
        }
    }
}

private class ExactRgbSet(capacityHint: Int) {
    private val keys = IntArray(tableSizeFor(capacityHint))
    private val used = BooleanArray(keys.size)

    fun add(key: Int) {
        var slot = mixRgb(key) and (keys.size - 1)
        while (true) {
            if (used[slot] && keys[slot] == key) return
            if (!used[slot]) {
                used[slot] = true
                keys[slot] = key
                return
            }
            slot = (slot + 1) and (keys.size - 1)
        }
    }

    fun contains(key: Int): Boolean {
        var slot = mixRgb(key) and (keys.size - 1)
        while (true) {
            if (!used[slot]) return false
            if (keys[slot] == key) return true
            slot = (slot + 1) and (keys.size - 1)
        }
    }
}

private fun tableSizeFor(capacityHint: Int): Int {
    var size = 1
    while (size < capacityHint) {
        size = size shl 1
    }
    return size
}

private class AdaptiveHistogram {
    private val exactKeys = IntArray(65_536) { -1 }
    private val exactWeights = LongArray(65_536)
    private val fallbackWeights = LongArray(ADAPTIVE_FALLBACK_BINS)
    private val fallbackRedSums = LongArray(ADAPTIVE_FALLBACK_BINS)
    private val fallbackGreenSums = LongArray(ADAPTIVE_FALLBACK_BINS)
    private val fallbackBlueSums = LongArray(ADAPTIVE_FALLBACK_BINS)

    var exactUnique: Int = 0
        private set
    var exactOverflowed: Boolean = false
        private set
    var visibleCount: Long = 0
        private set

    fun add(rgb: Int, alpha: Int) {
        visibleCount += 1L
        addFallback(rgb, alpha)
        if (!exactOverflowed) {
            addExact(rgb, alpha)
        }
    }

    fun entries(shouldCancel: () -> Boolean): Array<AdaptiveColorEntry>? {
        return if (!exactOverflowed) {
            exactEntries(shouldCancel) ?: return null
        } else {
            fallbackEntries(shouldCancel) ?: return null
        }.also {
            if (shouldCancel()) return null
            it.sortWith(compareBy<AdaptiveColorEntry> { entry -> entry.rgb })
            if (shouldCancel()) return null
        }.toTypedArray()
    }

    private fun addExact(rgb: Int, alpha: Int) {
        var slot = mixRgb(rgb) and (exactKeys.size - 1)
        while (true) {
            val existing = exactKeys[slot]
            if (existing == rgb) {
                exactWeights[slot] += alpha.toLong()
                return
            }
            if (existing == -1) {
                if (exactUnique == ADAPTIVE_EXACT_HISTOGRAM_LIMIT) {
                    exactOverflowed = true
                    return
                }
                exactKeys[slot] = rgb
                exactWeights[slot] = alpha.toLong()
                exactUnique += 1
                return
            }
            slot = (slot + 1) and (exactKeys.size - 1)
        }
    }

    private fun addFallback(rgb: Int, alpha: Int) {
        val red = (rgb ushr 16) and 0xFF
        val green = (rgb ushr 8) and 0xFF
        val blue = rgb and 0xFF
        val index = ((red ushr 3) shl 10) or ((green ushr 3) shl 5) or (blue ushr 3)
        val weight = alpha.toLong()
        fallbackWeights[index] += weight
        fallbackRedSums[index] += red.toLong() * weight
        fallbackGreenSums[index] += green.toLong() * weight
        fallbackBlueSums[index] += blue.toLong() * weight
    }

    private fun exactEntries(shouldCancel: () -> Boolean): ArrayList<AdaptiveColorEntry>? {
        val entries = ArrayList<AdaptiveColorEntry>(exactUnique)
        for (slot in exactKeys.indices) {
            if (slot != 0 && slot % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
            val rgb = exactKeys[slot]
            if (rgb != -1) {
                entries.add(AdaptiveColorEntry(rgb, exactWeights[slot], bin = rgb, lab = rgbToOklabQ14(rgb)))
            }
        }
        return entries
    }

    private fun fallbackEntries(shouldCancel: () -> Boolean): ArrayList<AdaptiveColorEntry>? {
        val entries = ArrayList<AdaptiveColorEntry>()
        for (index in 0 until ADAPTIVE_FALLBACK_BINS) {
            if (index != 0 && index % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
            val weight = fallbackWeights[index]
            if (weight == 0L) continue
            val red = ((fallbackRedSums[index] + weight / 2L) / weight).toInt().coerceIn(0, 255)
            val green = ((fallbackGreenSums[index] + weight / 2L) / weight).toInt().coerceIn(0, 255)
            val blue = ((fallbackBlueSums[index] + weight / 2L) / weight).toInt().coerceIn(0, 255)
            val labRgb = (red shl 16) or (green shl 8) or blue
            entries.add(AdaptiveColorEntry(labRgb, weight, bin = index, lab = rgbToOklabQ14(labRgb)))
        }
        return entries
    }
}

private data class AdaptiveColorEntry(
    val rgb: Int,
    val weight: Long,
    val bin: Int,
    val lab: OklabQ14,
)

private data class OklabQ14(
    val l: Int,
    val a: Int,
    val b: Int,
) {
    fun component(axis: Int): Int = when (axis) {
        0 -> l
        1 -> a
        else -> b
    }
}

private data class AdaptiveBox(
    val start: Int,
    val endExclusive: Int,
)

private data class AdaptiveBoxStats(
    val totalSse: Long,
    val axisSse: LongArray,
    val totalWeight: Long,
)

private fun generateAdaptiveAnchors(
    entries: Array<AdaptiveColorEntry>,
    maxRgbColors: Int,
    shouldCancel: () -> Boolean,
): List<Int>? {
    val targetCount = minOf(maxRgbColors, entries.size)
    val boxes = ArrayList<AdaptiveBox>()
    boxes.add(AdaptiveBox(0, entries.size))
    while (boxes.size < targetCount) {
        if (shouldCancel()) return null
        val splitIndex = chooseSplitBox(entries, boxes, shouldCancel) ?: return null
        if (splitIndex < 0) break
        val box = boxes.removeAt(splitIndex)
        val split = splitBox(entries, box, shouldCancel)
        if (split == null) {
            boxes.add(splitIndex, box)
            break
        }
        boxes.add(splitIndex, split.first)
        boxes.add(splitIndex + 1, split.second)
    }

    var anchorIndices = IntArray(boxes.size)
    for (i in boxes.indices) {
        if (i != 0 && i % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        anchorIndices[i] = representativeIndex(entries, boxes[i], shouldCancel) ?: return null
    }
    anchorIndices = repairAnchorIndices(entries, anchorIndices, targetCount, shouldCancel) ?: return null

    repeat(ADAPTIVE_LLOYD_REFINEMENTS) {
        if (shouldCancel()) return null
        anchorIndices = refineAnchors(entries, anchorIndices, targetCount, shouldCancel) ?: return null
    }

    val anchors = ArrayList<Int>(anchorIndices.size)
    val seen = HashSet<Int>(anchorIndices.size)
    for (index in anchorIndices) {
        val color = 0xFF000000.toInt() or entries[index].rgb
        if (seen.add(color and 0x00FFFFFF)) {
            anchors.add(color)
        }
    }
    anchors.sort()
    return Collections.unmodifiableList(anchors)
}

private fun chooseSplitBox(
    entries: Array<AdaptiveColorEntry>,
    boxes: List<AdaptiveBox>,
    shouldCancel: () -> Boolean,
): Int? {
    var bestIndex = -1
    var bestSse = Long.MIN_VALUE
    for (i in boxes.indices) {
        val box = boxes[i]
        if (box.endExclusive - box.start < 2) continue
        val stats = boxStats(entries, box, shouldCancel) ?: return null
        if (stats.totalSse > bestSse) {
            bestIndex = i
            bestSse = stats.totalSse
        }
    }
    return bestIndex
}

private fun splitBox(
    entries: Array<AdaptiveColorEntry>,
    box: AdaptiveBox,
    shouldCancel: () -> Boolean,
): Pair<AdaptiveBox, AdaptiveBox>? {
    val stats = boxStats(entries, box, shouldCancel) ?: return null
    val axis = greatestSseAxis(stats.axisSse)
    if (shouldCancel()) return null
    entries.sortWith(box.start, box.endExclusive) { left, right ->
        val componentCompare = left.lab.component(axis).compareTo(right.lab.component(axis))
        if (componentCompare != 0) componentCompare else left.rgb.compareTo(right.rgb)
    }
    if (shouldCancel()) return null
    val total = stats.totalWeight
    val half = (total + 1L) / 2L
    var cumulative = 0L
    var split = box.start + 1
    for (i in box.start until box.endExclusive) {
        if ((i - box.start) != 0 && (i - box.start) % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        cumulative += entries[i].weight
        if (cumulative >= half) {
            split = (i + 1).coerceIn(box.start + 1, box.endExclusive - 1)
            break
        }
    }
    if (shouldCancel()) return null
    entries.sortWith(box.start, split) { left, right -> left.rgb.compareTo(right.rgb) }
    entries.sortWith(split, box.endExclusive) { left, right -> left.rgb.compareTo(right.rgb) }
    if (shouldCancel()) return null
    return if (split <= box.start || split >= box.endExclusive) {
        null
    } else {
        AdaptiveBox(box.start, split) to AdaptiveBox(split, box.endExclusive)
    }
}

private inline fun <T> Array<T>.sortWith(
    fromIndex: Int,
    toIndex: Int,
    comparator: Comparator<in T>,
) {
    java.util.Arrays.sort(this, fromIndex, toIndex, comparator)
}

private fun boxStats(
    entries: Array<AdaptiveColorEntry>,
    box: AdaptiveBox,
    shouldCancel: () -> Boolean,
): AdaptiveBoxStats? {
    var total = 0L
    var sumL = 0L
    var sumA = 0L
    var sumB = 0L
    for (i in box.start until box.endExclusive) {
        if ((i - box.start) != 0 && (i - box.start) % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val entry = entries[i]
        val weight = entry.weight
        total += weight
        sumL += entry.lab.l.toLong() * weight
        sumA += entry.lab.a.toLong() * weight
        sumB += entry.lab.b.toLong() * weight
    }
    if (total <= 0L) return AdaptiveBoxStats(0L, longArrayOf(0L, 0L, 0L), total)
    val mean = OklabQ14(
        roundedDiv(sumL, total),
        roundedDiv(sumA, total),
        roundedDiv(sumB, total),
    )
    var sseL = 0L
    var sseA = 0L
    var sseB = 0L
    for (i in box.start until box.endExclusive) {
        if ((i - box.start) != 0 && (i - box.start) % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val entry = entries[i]
        val weight = entry.weight
        val dl = entry.lab.l.toLong() - mean.l.toLong()
        val da = entry.lab.a.toLong() - mean.a.toLong()
        val db = entry.lab.b.toLong() - mean.b.toLong()
        sseL += dl * dl * weight
        sseA += da * da * weight
        sseB += db * db * weight
    }
    return AdaptiveBoxStats(sseL + sseA + sseB, longArrayOf(sseL, sseA, sseB), total)
}

private fun greatestSseAxis(axisSse: LongArray): Int {
    return when {
        axisSse[0] >= axisSse[1] && axisSse[0] >= axisSse[2] -> 0
        axisSse[1] >= axisSse[2] -> 1
        else -> 2
    }
}

private fun representativeIndex(
    entries: Array<AdaptiveColorEntry>,
    box: AdaptiveBox,
    shouldCancel: () -> Boolean,
): Int? {
    var totalWeight = 0L
    var sumL = 0L
    var sumA = 0L
    var sumB = 0L
    for (i in box.start until box.endExclusive) {
        if ((i - box.start) != 0 && (i - box.start) % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val weight = entries[i].weight
        totalWeight += weight
        sumL += entries[i].lab.l.toLong() * weight
        sumA += entries[i].lab.a.toLong() * weight
        sumB += entries[i].lab.b.toLong() * weight
    }
    val target = OklabQ14(
        l = roundedDiv(sumL, totalWeight),
        a = roundedDiv(sumA, totalWeight),
        b = roundedDiv(sumB, totalWeight),
    )
    var best = box.start
    var bestDistance = Long.MAX_VALUE
    for (i in box.start until box.endExclusive) {
        if ((i - box.start) != 0 && (i - box.start) % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val distance = oklabDistanceSquaredQ14(entries[i].lab, target)
        if (distance < bestDistance || distance == bestDistance && entries[i].rgb < entries[best].rgb) {
            best = i
            bestDistance = distance
        }
    }
    return best
}

private fun refineAnchors(
    entries: Array<AdaptiveColorEntry>,
    anchorIndices: IntArray,
    targetCount: Int,
    shouldCancel: () -> Boolean,
): IntArray? {
    val assignments = IntArray(entries.size)
    val clusterWeights = LongArray(anchorIndices.size)
    val sumL = LongArray(anchorIndices.size)
    val sumA = LongArray(anchorIndices.size)
    val sumB = LongArray(anchorIndices.size)
    for (entryIndex in entries.indices) {
        if (entryIndex != 0 && entryIndex % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        var bestAnchor = 0
        var bestDistance = Long.MAX_VALUE
        for (anchorSlot in anchorIndices.indices) {
            val anchorIndex = anchorIndices[anchorSlot]
            val distance = oklabDistanceSquaredQ14(entries[entryIndex].lab, entries[anchorIndex].lab)
            if (
                distance < bestDistance ||
                distance == bestDistance && entries[anchorIndex].rgb < entries[anchorIndices[bestAnchor]].rgb
            ) {
                bestDistance = distance
                bestAnchor = anchorSlot
            }
        }
        assignments[entryIndex] = bestAnchor
        val entry = entries[entryIndex]
        val weight = entry.weight
        clusterWeights[bestAnchor] += weight
        sumL[bestAnchor] += entry.lab.l.toLong() * weight
        sumA[bestAnchor] += entry.lab.a.toLong() * weight
        sumB[bestAnchor] += entry.lab.b.toLong() * weight
    }

    val refined = IntArray(anchorIndices.size)
    for (anchorSlot in anchorIndices.indices) {
        if (anchorSlot != 0 && anchorSlot % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val clusterWeight = clusterWeights[anchorSlot]
        if (clusterWeight == 0L) {
            refined[anchorSlot] = anchorIndices[anchorSlot]
            continue
        }
        val target = OklabQ14(
            roundedDiv(sumL[anchorSlot], clusterWeight),
            roundedDiv(sumA[anchorSlot], clusterWeight),
            roundedDiv(sumB[anchorSlot], clusterWeight),
        )
        var best = -1
        var bestDistance = Long.MAX_VALUE
        for (entryIndex in entries.indices) {
            if (entryIndex != 0 && entryIndex % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
            if (assignments[entryIndex] != anchorSlot) continue
            val distance = oklabDistanceSquaredQ14(entries[entryIndex].lab, target)
            if (
                best == -1 ||
                distance < bestDistance ||
                distance == bestDistance && entries[entryIndex].rgb < entries[best].rgb
            ) {
                best = entryIndex
                bestDistance = distance
            }
        }
        refined[anchorSlot] = best
    }
    return repairAnchorIndices(entries, refined, targetCount, shouldCancel)
}

private fun repairAnchorIndices(
    entries: Array<AdaptiveColorEntry>,
    anchorIndices: IntArray,
    targetCount: Int,
    shouldCancel: () -> Boolean,
): IntArray? {
    val repaired = ArrayList<Int>(targetCount)
    val represented = BooleanArray(entries.size)
    for (i in anchorIndices.indices) {
        if (i != 0 && i % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
        val index = anchorIndices[i]
        if (index in entries.indices && !represented[index]) {
            represented[index] = true
            repaired.add(index)
        }
    }
    while (repaired.size < targetCount) {
        if (shouldCancel()) return null
        var best = -1
        var bestError = Long.MIN_VALUE
        for (entryIndex in entries.indices) {
            if (entryIndex != 0 && entryIndex % ADAPTIVE_CANCEL_ENTRY_INTERVAL == 0 && shouldCancel()) return null
            if (represented[entryIndex]) continue
            val error = nearestRepresentedError(entries, entryIndex, repaired)
            if (
                best == -1 ||
                error > bestError ||
                error == bestError && entries[entryIndex].rgb < entries[best].rgb
            ) {
                best = entryIndex
                bestError = error
            }
        }
        if (best == -1) break
        represented[best] = true
        repaired.add(best)
    }
    return repaired.toIntArray()
}

private fun nearestRepresentedError(
    entries: Array<AdaptiveColorEntry>,
    entryIndex: Int,
    represented: List<Int>,
): Long {
    if (represented.isEmpty()) return Long.MAX_VALUE
    var bestDistance = Long.MAX_VALUE
    val entry = entries[entryIndex]
    for (anchorIndex in represented) {
        val distance = oklabDistanceSquaredQ14(entry.lab, entries[anchorIndex].lab)
        if (distance < bestDistance) {
            bestDistance = distance
        }
    }
    return bestDistance * entry.weight
}

private fun roundedDiv(numerator: Long, denominator: Long): Int {
    return if (numerator >= 0L) {
        ((numerator + denominator / 2L) / denominator).toInt()
    } else {
        ((numerator - denominator / 2L) / denominator).toInt()
    }
}

private fun rgbToOklabQ14(rgb: Int): OklabQ14 {
    val red = srgbToLinearAdaptive(((rgb ushr 16) and 0xFF) / 255.0)
    val green = srgbToLinearAdaptive(((rgb ushr 8) and 0xFF) / 255.0)
    val blue = srgbToLinearAdaptive((rgb and 0xFF) / 255.0)

    val l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
    val m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
    val s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue

    val lRoot = StrictMath.cbrt(l)
    val mRoot = StrictMath.cbrt(m)
    val sRoot = StrictMath.cbrt(s)

    return OklabQ14(
        l = StrictMath.round((0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot) * OKLAB_Q14).toInt(),
        a = StrictMath.round((1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot) * OKLAB_Q14).toInt(),
        b = StrictMath.round((0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot) * OKLAB_Q14).toInt(),
    )
}

private fun srgbToLinearAdaptive(channel: Double): Double {
    return if (channel <= 0.04045) {
        channel / 12.92
    } else {
        StrictMath.pow((channel + 0.055) / 1.055, 2.4)
    }
}

private fun oklabDistanceSquaredQ14(left: OklabQ14, right: OklabQ14): Long {
    val dl = left.l.toLong() - right.l.toLong()
    val da = left.a.toLong() - right.a.toLong()
    val db = left.b.toLong() - right.b.toLong()
    return dl * dl + da * da + db * db
}

private fun physicalPremultipliedKeyAdaptive(alpha: Int, rgb: Int): Int {
    return (alpha shl 24) or
        (premultipliedChannelAdaptive((rgb ushr 16) and 0xFF, alpha) shl 16) or
        (premultipliedChannelAdaptive((rgb ushr 8) and 0xFF, alpha) shl 8) or
        premultipliedChannelAdaptive(rgb and 0xFF, alpha)
}

private fun fiveBitBin(rgb: Int): Int {
    return ((((rgb ushr 16) and 0xFF) ushr 3) shl 10) or
        ((((rgb ushr 8) and 0xFF) ushr 3) shl 5) or
        ((rgb and 0xFF) ushr 3)
}

private fun binCenterRgb(bin: Int): Int {
    val red = (((bin ushr 10) and 31) shl 3) or 4
    val green = (((bin ushr 5) and 31) shl 3) or 4
    val blue = ((bin and 31) shl 3) or 4
    return (red shl 16) or (green shl 8) or blue
}

private fun premultipliedChannelAdaptive(channel: Int, alpha: Int): Int {
    return (channel * alpha + 127) / 255
}

private fun mixRgb(rgb: Int): Int {
    var value = rgb
    value = value xor (value ushr 16)
    value *= 0x7FEB352D
    value = value xor (value ushr 15)
    value *= 0x846CA68B.toInt()
    value = value xor (value ushr 16)
    return value
}
