package io.github.ninbyo02.lami.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val BINARIZE_ALPHA_THRESHOLD = 16
const val BINARIZE_FALLBACK_THRESHOLD = 128
private const val BINARIZE_MIN_VALID_PIXELS = 16
private const val BINARIZE_MIN_OTSU_THRESHOLD = 40
private const val BINARIZE_MAX_OTSU_THRESHOLD = 220
private const val CLEAR_BG_EDGE_SAMPLE_LIMIT = 32
private const val CLEAR_BG_COLOR_DISTANCE_THRESHOLD = 40
private const val CLEAR_BG_MIN_ALPHA = 8
private const val CLEAR_REGION_COLOR_DISTANCE_THRESHOLD = 30
private const val FILL_REGION_ABSOLUTE_MAX_PIXELS = 2_000_000
const val SPRITE_BITMAP_OPS_MAX_PIXELS = 4_194_304
// Fill Connectedで透明とみなすalphaの上限値（alpha=0以外のほぼ透明背景も対象にする）
const val FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD = 8
const val FILL_CONNECTED_RGB_TOLERANCE = 24

internal val LEGACY_FIXED_SPRITE_PALETTE_V1: List<Int> = buildLegacyFixedSpritePaletteV1()
val FIXED_SPRITE_PALETTE: List<Int> = buildFixedSpritePaletteV2()

data class PaletteBitmapResult(
    val bitmap: Bitmap,
    val changed: Boolean,
    val rejectionReason: PaletteBitmapRejectionReason = PaletteBitmapRejectionReason.NONE,
    val cancelled: Boolean = false,
    val rgbAnchors: List<Int> = emptyList(),
) {
    val rejected: Boolean
        get() = rejectionReason != PaletteBitmapRejectionReason.NONE
}

enum class UniformSelectionColorStatus {
    UNIFORM,
    MIXED,
    TRANSPARENT,
    CANCELLED,
    TOO_LARGE,
    RECYCLED,
    UNSUPPORTED_CONFIG,
    READ_FAILED,
}

data class UniformSelectionColorResult(
    val status: UniformSelectionColorStatus,
    val color: Int? = null,
)

fun findUniformSelectionColor(
    bitmap: Bitmap,
    selection: RectPx,
    rowBufferAllocator: (size: Int) -> IntArray = { size -> IntArray(size) },
    shouldCancel: (row: Int) -> Boolean = { false },
): UniformSelectionColorResult {
    if (bitmap.isRecycled) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.RECYCLED)
    }
    if (bitmap.config == Bitmap.Config.HARDWARE) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.UNSUPPORTED_CONFIG)
    }
    if (bitmap.width < 1 || bitmap.height < 1) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.READ_FAILED)
    }

    val safeSelection = rectNormalizeClamp(selection, bitmap.width, bitmap.height)
    val selectionPixels = safeSelection.w.toLong() * safeSelection.h.toLong()
    if (selectionPixels > SPRITE_BITMAP_OPS_MAX_PIXELS) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.TOO_LARGE)
    }

    val rowPixels = try {
        rowBufferAllocator(safeSelection.w)
    } catch (_: OutOfMemoryError) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.READ_FAILED)
    }
    if (rowPixels.size != safeSelection.w) {
        return UniformSelectionColorResult(UniformSelectionColorStatus.READ_FAILED)
    }
    var firstColor: Int? = null
    var allTransparent = true
    for (row in 0 until safeSelection.h) {
        if (shouldCancel(row)) {
            return UniformSelectionColorResult(UniformSelectionColorStatus.CANCELLED)
        }
        try {
            bitmap.getPixels(
                rowPixels,
                0,
                safeSelection.w,
                safeSelection.x,
                safeSelection.y + row,
                safeSelection.w,
                1,
            )
        } catch (_: RuntimeException) {
            return UniformSelectionColorResult(UniformSelectionColorStatus.READ_FAILED)
        }
        for (pixel in rowPixels) {
            if (Color.alpha(pixel) != 0) {
                allTransparent = false
            }
            val expected = firstColor
            if (expected == null) {
                firstColor = pixel
            } else if (pixel != expected) {
                if (!allTransparent) {
                    return UniformSelectionColorResult(UniformSelectionColorStatus.MIXED)
                }
            }
        }
    }

    val color = firstColor ?: return UniformSelectionColorResult(UniformSelectionColorStatus.READ_FAILED)
    return if (allTransparent) {
        UniformSelectionColorResult(UniformSelectionColorStatus.TRANSPARENT)
    } else {
        UniformSelectionColorResult(UniformSelectionColorStatus.UNIFORM, color)
    }
}

enum class PaletteBitmapRejectionReason {
    NONE,
    TOO_LARGE,
    RECYCLED,
    UNSUPPORTED_CONFIG,
    COPY_FAILED,
    READ_FAILED,
    WRITE_FAILED,
    CANCELLED,
}

enum class Mode { Alpha, Rgb }

enum class FillConnectedSeedType {
    White,
    Black,
    Transparent,
    Other,
    None,
}

data class FillConnectedResult(
    val bitmap: Bitmap,
    val filled: Int,
    val aborted: Boolean,
    val mode: Mode,
    val debugText: String,
    val seedType: FillConnectedSeedType,
)

data class TransparentSelectionStats(
    val transparentCount: Int,
    val threshold: Int,
    val minAlpha: Int,
    val maxAlpha: Int,
)

data class ResizeSelectionResult(
    val bitmap: Bitmap,
    val selection: RectPx,
    val applied: Boolean,
    val debugText: String,
)

private data class OklabColor(
    val l: Double,
    val a: Double,
    val b: Double,
)

private data class PaletteOklabEntry(
    val index: Int,
    val color: Int,
    val oklab: OklabColor,
)

private data class PaletteKdNode(
    val entry: PaletteOklabEntry,
    val axis: Int,
    val left: PaletteKdNode?,
    val right: PaletteKdNode?,
)

private const val NEAREST_PALETTE_CACHE_MAX_SIZE = 4096

private class SpritePaletteIndex(
    val colors: List<Int>,
) {
    val entries: List<PaletteOklabEntry> = colors.mapIndexed { index, color ->
        PaletteOklabEntry(index = index, color = color, oklab = colorToOklab(color))
    }
    val rgbSet: Set<Int> = colors.mapTo(HashSet(colors.size)) { it and 0x00FFFFFF }
    val physicalPremultipliedKeysByAlpha: Array<Set<Int>> = Array(256) { alpha ->
        colors.mapTo(HashSet(colors.size)) { color -> physicalPremultipliedKey(alpha, color) }
    }
    val kdTree: PaletteKdNode? = buildPaletteKdTree(entries, depth = 0)
    val cache = object : LinkedHashMap<Int, Int>(
        NEAREST_PALETTE_CACHE_MAX_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Int>?): Boolean {
            return size > NEAREST_PALETTE_CACHE_MAX_SIZE
        }
    }

    fun nearest(color: Int): Int {
        val opaqueRgb = color and 0x00FFFFFF
        if (opaqueRgb in rgbSet) {
            return 0xFF000000.toInt() or opaqueRgb
        }
        synchronized(cache) {
            cache[opaqueRgb]?.let { return it }
        }
        val target = colorToOklab(0xFF000000.toInt() or opaqueRgb)
        val nearest = nearestPaletteEntry(target, entries, kdTree).color
        synchronized(cache) {
            cache[opaqueRgb] = nearest
        }
        return nearest
    }

    fun isNoOp(pixel: Int, isPremultiplied: Boolean): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        return if (isPremultiplied) {
            physicalPremultipliedKey(alpha, pixel) in physicalPremultipliedKeysByAlpha[alpha]
        } else {
            (pixel and 0x00FFFFFF) in rgbSet
        }
    }
}

private val FIXED_SPRITE_PALETTE_V2_INDEX: SpritePaletteIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SpritePaletteIndex(FIXED_SPRITE_PALETTE)
}
private val LEGACY_FIXED_SPRITE_PALETTE_V1_INDEX: SpritePaletteIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SpritePaletteIndex(LEGACY_FIXED_SPRITE_PALETTE_V1)
}

fun fixedSpritePalette(): List<Int> = FIXED_SPRITE_PALETTE

internal data class SpritePaletteDisplaySection(
    val label: String,
    val colors: List<Int>,
)

private val FIXED_SPRITE_PALETTE_DISPLAY_SECTIONS: List<SpritePaletteDisplaySection> =
    buildFixedSpritePaletteDisplaySections()

internal fun fixedSpritePaletteDisplaySections(): List<SpritePaletteDisplaySection> {
    return FIXED_SPRITE_PALETTE_DISPLAY_SECTIONS
}

private fun buildFixedSpritePaletteDisplaySections(): List<SpritePaletteDisplaySection> {
    val labels = listOf("Grayscale", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Magenta")
    val sectionSizes = intArrayOf(32, 28, 28, 28, 28, 28, 28, 28, 28)
    val sections = ArrayList<SpritePaletteDisplaySection>(labels.size)
    var start = 0
    labels.forEachIndexed { index, label ->
        val end = start + sectionSizes[index]
        sections.add(
            SpritePaletteDisplaySection(
                label = label,
                colors = Collections.unmodifiableList(FIXED_SPRITE_PALETTE.subList(start, end).toList()),
            ),
        )
        start = end
    }
    check(start == FIXED_SPRITE_PALETTE.size)
    return Collections.unmodifiableList(sections)
}

private fun buildLegacyFixedSpritePaletteV1(): List<Int> {
    val levels = intArrayOf(0, 51, 102, 153, 204, 255)
    val colors = ArrayList<Int>(256)
    for (red in levels) {
        for (green in levels) {
            for (blue in levels) {
                colors.add(Color.rgb(red, green, blue))
            }
        }
    }

    val cubeGrayLevels = levels.toSet()
    for (step in 1 until 45) {
        val gray = (step * 255.0 / 45.0).roundToInt()
        if (gray !in cubeGrayLevels) {
            colors.add(Color.rgb(gray, gray, gray))
        }
    }

    check(colors.size == 256)
    check(colors.toSet().size == 256)
    return Collections.unmodifiableList(colors.toList())
}

private fun buildFixedSpritePaletteV2(): List<Int> {
    val colors = ArrayList<Int>(256)
    val grayCodes = intArrayOf(
        0x00, 0x01, 0x03, 0x07, 0x0D, 0x14, 0x1B, 0x22,
        0x2A, 0x32, 0x3A, 0x42, 0x4A, 0x52, 0x5B, 0x64,
        0x6D, 0x76, 0x7F, 0x88, 0x91, 0x9B, 0xA4, 0xAE,
        0xB8, 0xC2, 0xCC, 0xD6, 0xE0, 0xEA, 0xF5, 0xFF,
    )
    grayCodes.forEach { gray ->
        colors.add(Color.rgb(gray, gray, gray))
    }

    val lightnessRows = doubleArrayOf(0.22, 0.33, 0.44, 0.55, 0.66, 0.77, 0.88)
    val chromaFractions = doubleArrayOf(0.25, 0.45, 0.70, 0.95)
    val hueCenters = doubleArrayOf(29.0, 65.0, 105.0, 142.0, 195.0, 264.0, 295.0, 330.0)
    val anchors = intArrayOf(
        Color.rgb(255, 0, 0),
        Color.rgb(255, 128, 0),
        Color.rgb(255, 255, 0),
        Color.rgb(0, 255, 0),
        Color.rgb(0, 255, 255),
        Color.rgb(0, 0, 255),
        Color.rgb(128, 0, 255),
        Color.rgb(255, 0, 255),
    )

    hueCenters.forEachIndexed { hueIndex, hue ->
        val anchorRow = nearestLightnessRowIndex(lightnessRows, colorToOklab(anchors[hueIndex]).l)
        lightnessRows.forEachIndexed { rowIndex, lightness ->
            val maxChroma = maxDisplayableOklchChroma(lightness, hue)
            chromaFractions.forEachIndexed { chromaIndex, fraction ->
                val color = if (rowIndex == anchorRow && chromaIndex == chromaFractions.lastIndex) {
                    anchors[hueIndex]
                } else {
                    oklchToSrgbColor(lightness, maxChroma * fraction, hue)
                }
                colors.add(color)
            }
        }
    }

    check(colors.size == 256)
    check(colors.toSet().size == 256)
    check(colors.all { Color.alpha(it) == 255 })
    return Collections.unmodifiableList(colors.toList())
}

private fun nearestLightnessRowIndex(lightnessRows: DoubleArray, anchorLightness: Double): Int {
    var bestIndex = 0
    var bestDistance = StrictMath.abs(lightnessRows[0] - anchorLightness)
    for (index in 1 until lightnessRows.size) {
        val distance = StrictMath.abs(lightnessRows[index] - anchorLightness)
        if (distance < bestDistance) {
            bestIndex = index
            bestDistance = distance
        }
    }
    return bestIndex
}

private fun maxDisplayableOklchChroma(lightness: Double, hueDegrees: Double): Double {
    var low = 0.0
    var high = 0.6
    repeat(32) {
        val mid = (low + high) * 0.5
        if (oklchLinearSrgbInGamut(lightness, mid, hueDegrees)) {
            low = mid
        } else {
            high = mid
        }
    }
    return low
}

private fun oklchToSrgbColor(lightness: Double, chroma: Double, hueDegrees: Double): Int {
    val rgb = oklchToLinearSrgb(lightness, chroma, hueDegrees)
    return Color.rgb(
        linearSrgbToByte(rgb[0]),
        linearSrgbToByte(rgb[1]),
        linearSrgbToByte(rgb[2]),
    )
}

private fun oklchLinearSrgbInGamut(lightness: Double, chroma: Double, hueDegrees: Double): Boolean {
    val rgb = oklchToLinearSrgb(lightness, chroma, hueDegrees)
    return rgb[0] in 0.0..1.0 && rgb[1] in 0.0..1.0 && rgb[2] in 0.0..1.0
}

private fun oklchToLinearSrgb(lightness: Double, chroma: Double, hueDegrees: Double): DoubleArray {
    val hueRadians = StrictMath.toRadians(hueDegrees)
    val a = chroma * StrictMath.cos(hueRadians)
    val b = chroma * StrictMath.sin(hueRadians)

    val lPrime = lightness + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = lightness - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = lightness - 0.0894841775 * a - 1.2914855480 * b

    val l = lPrime * lPrime * lPrime
    val m = mPrime * mPrime * mPrime
    val s = sPrime * sPrime * sPrime

    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

private fun linearSrgbToByte(channel: Double): Int {
    val clamped = channel.coerceIn(0.0, 1.0)
    val encoded = if (clamped <= 0.0031308) {
        12.92 * clamped
    } else {
        1.055 * StrictMath.pow(clamped, 1.0 / 2.4) - 0.055
    }
    return StrictMath.floor(encoded * 255.0 + 0.5).toInt().coerceIn(0, 255)
}

fun nearestFixedPaletteColor(color: Int): Int = FIXED_SPRITE_PALETTE_V2_INDEX.nearest(color)

internal fun nearestLegacyFixedPaletteColor(color: Int): Int = LEGACY_FIXED_SPRITE_PALETTE_V1_INDEX.nearest(color)

private fun buildPaletteKdTree(entries: List<PaletteOklabEntry>, depth: Int): PaletteKdNode? {
    if (entries.isEmpty()) return null
    val axis = depth % 3
    val sorted = entries.sortedWith(compareBy<PaletteOklabEntry> { it.oklab.component(axis) }.thenBy { it.index })
    val median = sorted.size / 2
    return PaletteKdNode(
        entry = sorted[median],
        axis = axis,
        left = buildPaletteKdTree(sorted.subList(0, median), depth + 1),
        right = buildPaletteKdTree(sorted.subList(median + 1, sorted.size), depth + 1),
    )
}

private fun nearestPaletteEntry(
    target: OklabColor,
    entries: List<PaletteOklabEntry>,
    kdTree: PaletteKdNode?,
): PaletteOklabEntry {
    var best = entries.first()
    var bestDistance = oklabDistanceSquared(target, best.oklab)

    fun visit(node: PaletteKdNode?) {
        if (node == null) return
        val candidateDistance = oklabDistanceSquared(target, node.entry.oklab)
        if (
            candidateDistance < bestDistance ||
            (candidateDistance == bestDistance && node.entry.index < best.index)
        ) {
            best = node.entry
            bestDistance = candidateDistance
        }

        val delta = target.component(node.axis) - node.entry.oklab.component(node.axis)
        val near = if (delta <= 0.0) node.left else node.right
        val far = if (delta <= 0.0) node.right else node.left
        visit(near)
        if (delta * delta <= bestDistance) {
            visit(far)
        }
    }

    visit(kdTree)
    return best
}

private fun OklabColor.component(axis: Int): Double {
    return when (axis) {
        0 -> l
        1 -> a
        else -> b
    }
}

private fun oklabDistanceSquared(left: OklabColor, right: OklabColor): Double {
    val dl = left.l - right.l
    val da = left.a - right.a
    val db = left.b - right.b
    return dl * dl + da * da + db * db
}

fun reduceToFixedPalette(
    src: Bitmap,
    rowBufferAllocator: (size: Int) -> IntArray = { size -> IntArray(size) },
    shouldCancel: (row: Int) -> Boolean = { false },
): PaletteBitmapResult = reduceToSpritePalette(
    src,
    FIXED_SPRITE_PALETTE_V2_INDEX,
    rowBufferAllocator,
    shouldCancel,
)

internal fun reduceToLegacyFixedPalette(
    src: Bitmap,
    rowBufferAllocator: (size: Int) -> IntArray = { size -> IntArray(size) },
    shouldCancel: (row: Int) -> Boolean = { false },
): PaletteBitmapResult = reduceToSpritePalette(
    src,
    LEGACY_FIXED_SPRITE_PALETTE_V1_INDEX,
    rowBufferAllocator,
    shouldCancel,
)

private fun reduceToSpritePalette(
    src: Bitmap,
    paletteIndex: SpritePaletteIndex,
    rowBufferAllocator: (size: Int) -> IntArray,
    shouldCancel: (row: Int) -> Boolean,
): PaletteBitmapResult {
    if (src.isRecycled) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.RECYCLED,
        )
    }
    val width = src.width
    val height = src.height
    if (width <= 0 || height <= 0) {
        return PaletteBitmapResult(src, changed = false)
    }
    if (width.toLong() * height.toLong() > SPRITE_BITMAP_OPS_MAX_PIXELS) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.TOO_LARGE,
        )
    }

    val readable = readableArgb8888Bitmap(src) ?: return PaletteBitmapResult(
        src,
        changed = false,
        rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
    )
    val safeSrc = readable.bitmap
    val isPremultiplied = safeSrc.isPremultiplied
    val rowPixels = try {
        rowBufferAllocator(width)
    } catch (_: OutOfMemoryError) {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    if (rowPixels.size != width) {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    val output = runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPremultiplied(isPremultiplied)
        }
    }.getOrElse {
        readable.recycleIfNew()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    var changed = false
    for (y in 0 until height) {
        if (shouldCancel(y)) {
            output.recycle()
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.CANCELLED,
                cancelled = true,
            )
        }
        if (!runCatching { safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1) }.isSuccess) {
            output.recycle()
            readable.recycleIfNew()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.READ_FAILED,
            )
        }
        for (x in 0 until width) {
            val pixel = rowPixels[x]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha == 0) {
                continue
            }
            if (paletteIndex.isNoOp(pixel, isPremultiplied)) {
                continue
            }
            val nearest = paletteIndex.nearest(pixel)
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
            )
        }
    }

    readable.recycleIfNew()
    if (!changed) {
        output.recycle()
        return PaletteBitmapResult(src, changed = false)
    }
    return PaletteBitmapResult(output, changed = true)
}

fun fillSelectionWithColor(
    src: Bitmap,
    selection: RectPx,
    color: Int,
    rowBufferAllocator: (size: Int) -> IntArray = { size -> IntArray(size) },
    shouldCancel: (row: Int) -> Boolean = { false },
): PaletteBitmapResult {
    if (src.isRecycled) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.RECYCLED,
        )
    }
    val width = src.width
    val height = src.height
    if (width <= 0 || height <= 0) {
        return PaletteBitmapResult(src, changed = false)
    }
    if (width.toLong() * height.toLong() > SPRITE_BITMAP_OPS_MAX_PIXELS) {
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.TOO_LARGE,
        )
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val fillColor = color
    val output = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull()
        ?: return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    val rowPixels = try {
        rowBufferAllocator(safeSelection.w)
    } catch (_: OutOfMemoryError) {
        output.recycle()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    if (rowPixels.size != safeSelection.w) {
        output.recycle()
        return PaletteBitmapResult(
            src,
            changed = false,
            rejectionReason = PaletteBitmapRejectionReason.COPY_FAILED,
        )
    }
    var changed = false
    for (y in safeSelection.y until safeSelection.y + safeSelection.h) {
        if (shouldCancel(y)) {
            output.recycle()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.CANCELLED,
                cancelled = true,
            )
        }
        if (!runCatching {
            output.getPixels(rowPixels, 0, safeSelection.w, safeSelection.x, y, safeSelection.w, 1)
        }.isSuccess) {
            output.recycle()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.READ_FAILED,
            )
        }
        for (x in rowPixels.indices) {
            if (rowPixels[x] != fillColor) {
                rowPixels[x] = fillColor
                changed = true
            }
        }
        if (!runCatching {
            output.setPixels(rowPixels, 0, safeSelection.w, safeSelection.x, y, safeSelection.w, 1)
        }.isSuccess) {
            output.recycle()
            return PaletteBitmapResult(
                src,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.WRITE_FAILED,
            )
        }
    }

    if (!changed) {
        output.recycle()
        return PaletteBitmapResult(src, changed = false)
    }
    return PaletteBitmapResult(output, changed = true)
}

private data class ReadableBitmap(
    val bitmap: Bitmap,
    val isNew: Boolean,
) {
    fun recycleIfNew() {
        if (isNew && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private fun readableArgb8888Bitmap(src: Bitmap): ReadableBitmap? {
    if (src.config == Bitmap.Config.ARGB_8888) {
        return ReadableBitmap(src, isNew = false)
    }
    val copy = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull() ?: return null
    return ReadableBitmap(copy, isNew = true)
}

private fun physicalPremultipliedKey(alpha: Int, color: Int): Int {
    return (alpha shl 24) or
        (premultipliedChannel((color ushr 16) and 0xFF, alpha) shl 16) or
        (premultipliedChannel((color ushr 8) and 0xFF, alpha) shl 8) or
        premultipliedChannel(color and 0xFF, alpha)
}

private fun premultipliedChannel(channel: Int, alpha: Int): Int {
    return (channel * alpha + 127) / 255
}

private fun colorToOklab(color: Int): OklabColor {
    val red = srgbToLinear(((color ushr 16) and 0xFF) / 255.0)
    val green = srgbToLinear(((color ushr 8) and 0xFF) / 255.0)
    val blue = srgbToLinear((color and 0xFF) / 255.0)

    val l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
    val m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
    val s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue

    val lRoot = StrictMath.cbrt(l)
    val mRoot = StrictMath.cbrt(m)
    val sRoot = StrictMath.cbrt(s)

    return OklabColor(
        l = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
        a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
        b = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot,
    )
}

private fun srgbToLinear(channel: Double): Double {
    return if (channel <= 0.04045) {
        channel / 12.92
    } else {
        StrictMath.pow((channel + 0.055) / 1.055, 2.4)
    }
}

enum class ResizeDownscaleMode {
    DefaultMultiStep,
    PixelArtStable,
    LegacyMaxAlpha,
}

enum class PixelArtStableMethod {
    CenterSample,
    DarkDominant,
}

enum class ResizeAnchor {
    TopLeft,
    Center,
}

internal enum class CanvasStretchMode {
    None,
    StretchWidthToHeight,
    StretchHeightToWidth,
}

internal fun calculateCanvasStretchTargetSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedWidth: Int,
    requestedHeight: Int,
    stretchMode: CanvasStretchMode,
): androidx.compose.ui.unit.IntSize {
    val safeRequestedWidth = requestedWidth.coerceAtLeast(1)
    val safeRequestedHeight = requestedHeight.coerceAtLeast(1)
    return when (stretchMode) {
        CanvasStretchMode.None -> androidx.compose.ui.unit.IntSize(safeRequestedWidth, safeRequestedHeight)
        CanvasStretchMode.StretchWidthToHeight -> {
            val target = safeRequestedHeight
            androidx.compose.ui.unit.IntSize(target, target)
        }

        CanvasStretchMode.StretchHeightToWidth -> {
            val target = safeRequestedWidth
            androidx.compose.ui.unit.IntSize(target, target)
        }
    }
}

// Bitmapのキャンバスサイズを変更する（元のBitmapは変更しない）
fun resizeCanvas(
    src: Bitmap,
    newW: Int,
    newH: Int,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val safeW = newW.coerceAtLeast(1)
    val safeH = newH.coerceAtLeast(1)
    val output = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
    output.eraseColor(0)
    val dstX = when (anchor) {
        ResizeAnchor.TopLeft -> 0
        ResizeAnchor.Center -> (safeW - safeSrc.width) / 2
    }
    val dstY = when (anchor) {
        ResizeAnchor.TopLeft -> 0
        ResizeAnchor.Center -> (safeH - safeSrc.height) / 2
    }
    val canvas = Canvas(output)
    canvas.drawBitmap(safeSrc, dstX.toFloat(), dstY.toFloat(), null)
    return output
}

internal fun stretchCanvasToSize(
    src: Bitmap,
    newW: Int,
    newH: Int,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val safeW = newW.coerceAtLeast(1)
    val safeH = newH.coerceAtLeast(1)
    val output = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
    val srcPixels = IntArray(safeSrc.width * safeSrc.height)
    safeSrc.getPixels(srcPixels, 0, safeSrc.width, 0, 0, safeSrc.width, safeSrc.height)
    val outPixels = IntArray(safeW * safeH)
    for (y in 0 until safeH) {
        val srcY = (((y + 0.5f) * safeSrc.height) / safeH).toInt().coerceIn(0, safeSrc.height - 1)
        for (x in 0 until safeW) {
            val srcX = (((x + 0.5f) * safeSrc.width) / safeW).toInt().coerceIn(0, safeSrc.width - 1)
            outPixels[y * safeW + x] = srcPixels[srcY * safeSrc.width + srcX]
        }
    }
    output.setPixels(outPixels, 0, safeW, 0, 0, safeW, safeH)
    return output
}

fun countTransparentLikeInSelection(
    bitmap: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): TransparentSelectionStats {
    val safeBitmap = ensureArgb8888(bitmap)
    val width = safeBitmap.width
    val height = safeBitmap.height
    if (width <= 0 || height <= 0) {
        return TransparentSelectionStats(
            transparentCount = 0,
            threshold = transparentAlphaThreshold,
            minAlpha = 0,
            maxAlpha = 0,
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeBitmap.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var transparentCount = 0
    var minAlpha = 255
    var maxAlpha = 0
    for (pixel in selectionPixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha <= transparentAlphaThreshold) {
            transparentCount += 1
        }
        minAlpha = minOf(minAlpha, alpha)
        maxAlpha = maxOf(maxAlpha, alpha)
    }

    return TransparentSelectionStats(
        transparentCount = transparentCount,
        threshold = transparentAlphaThreshold,
        minAlpha = minAlpha,
        maxAlpha = maxAlpha,
    )
}

// 既存BitmapをARGB_8888で複製する（元のBitmapは変更しない）
fun ensureArgb8888(src: Bitmap): Bitmap {
    return if (src.config == Bitmap.Config.ARGB_8888) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src.copy(Bitmap.Config.ARGB_8888, false)
    }
}

// 選択矩形を画像範囲内に正規化する（矩形の最小サイズを維持）
fun rectNormalizeClamp(rect: RectPx, imageW: Int, imageH: Int): RectPx {
    val safeImageW = imageW.coerceAtLeast(1)
    val safeImageH = imageH.coerceAtLeast(1)
    val safeW = rect.w.coerceAtLeast(1).coerceAtMost(safeImageW)
    val safeH = rect.h.coerceAtLeast(1).coerceAtMost(safeImageH)
    val maxX = (safeImageW - safeW).coerceAtLeast(0)
    val maxY = (safeImageH - safeH).coerceAtLeast(0)
    val safeX = rect.x.coerceIn(0, maxX)
    val safeY = rect.y.coerceIn(0, maxY)
    return RectPx.of(safeX, safeY, safeW, safeH)
}

// 指定矩形を切り出した新しいBitmapを返す（元のBitmapは変更しない）
fun copyRect(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    return Bitmap.createBitmap(src, safeRect.x, safeRect.y, safeRect.w, safeRect.h)
}

// 画像を水平反転した新しいBitmapを返す（元のBitmapは変更しない）
fun flipHorizontal(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rowPixels = IntArray(width)
    val flippedRow = IntArray(width)
    for (y in 0 until height) {
        safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            flippedRow[width - 1 - x] = rowPixels[x]
        }
        output.setPixels(flippedRow, 0, width, 0, y, width, 1)
    }
    return output
}

// 指定矩形を透明でクリアした新しいBitmapを返す（元のBitmapは変更しない）
fun clearTransparent(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    for (y in safeRect.y until (safeRect.y + safeRect.h)) {
        for (x in safeRect.x until (safeRect.x + safeRect.w)) {
            output.setPixel(x, y, Color.TRANSPARENT)
        }
    }
    return output
}

// 指定矩形を黒で塗りつぶした新しいBitmapを返す（元のBitmapは変更しない）
fun fillBlack(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    val paint = Paint().apply { color = android.graphics.Color.BLACK }
    canvas.drawRect(
        safeRect.x.toFloat(),
        safeRect.y.toFloat(),
        (safeRect.x + safeRect.w).toFloat(),
        (safeRect.y + safeRect.h).toFloat(),
        paint
    )
    return output
}

fun findContentBoundsInRect(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): RectPx? {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return null
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeSrc.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var minX = safeSelection.w
    var minY = safeSelection.h
    var maxX = -1
    var maxY = -1
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val alpha = (selectionPixels[rowOffset + x] ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }

    if (maxX < 0 || maxY < 0) {
        return null
    }

    return RectPx.of(
        x = minX,
        y = minY,
        w = maxX - minX + 1,
        h = maxY - minY + 1,
    )
}

fun centerContentInRect(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeSrc.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var minX = safeSelection.w
    var minY = safeSelection.h
    var maxX = -1
    var maxY = -1
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val alpha = (selectionPixels[rowOffset + x] ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }

    if (maxX < 0 || maxY < 0) {
        return safeSrc
    }

    val contentCenterX = (minX + maxX) / 2
    val contentCenterY = (minY + maxY) / 2
    val selectionCenterX = (safeSelection.w - 1) / 2
    val selectionCenterY = (safeSelection.h - 1) / 2
    val dx = selectionCenterX - contentCenterX
    val dy = selectionCenterY - contentCenterY
    if (dx == 0 && dy == 0) {
        return safeSrc
    }

    val outputPixels = selectionPixels.copyOf()
    val contentPixels = IntArray(selectionPixels.size)
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val index = rowOffset + x
            val pixel = selectionPixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                contentPixels[index] = pixel
                outputPixels[index] = 0
            }
        }
    }

    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val index = rowOffset + x
            val pixel = contentPixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                val dstX = x + dx
                val dstY = y + dy
                if (dstX in 0 until safeSelection.w && dstY in 0 until safeSelection.h) {
                    outputPixels[dstY * safeSelection.w + dstX] = pixel
                }
            }
        }
    }

    val output = safeSrc.copy(Bitmap.Config.ARGB_8888, true)
    output.setPixels(
        outputPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )
    return output
}

// クリップBitmapを貼り付けた新しいBitmapを返す（元のBitmapは変更しない）
fun paste(src: Bitmap, clip: Bitmap, dstX: Int, dstY: Int): Bitmap {
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    var safeDstX = dstX
    var safeDstY = dstY
    var srcX = 0
    var srcY = 0
    if (safeDstX < 0) {
        srcX = -safeDstX
        safeDstX = 0
    }
    if (safeDstY < 0) {
        srcY = -safeDstY
        safeDstY = 0
    }
    val maxW = minOf(clip.width - srcX, src.width - safeDstX)
    val maxH = minOf(clip.height - srcY, src.height - safeDstY)
    if (maxW <= 0 || maxH <= 0) {
        return output
    }
    val srcRect = Rect(srcX, srcY, srcX + maxW, srcY + maxH)
    val dstRect = Rect(safeDstX, safeDstY, safeDstX + maxW, safeDstY + maxH)
    val canvas = Canvas(output)
    canvas.drawBitmap(clip, srcRect, dstRect, null)
    return output
}

// 9点サンプリング(3x3)でpremultiplied alpha平均のダウンサンプル
fun downscaleNineSamplePremul(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
): IntArray {
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val output = IntArray(safeDstW * safeDstH)
    val samplePoints = floatArrayOf(0.17f, 0.5f, 0.83f)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    val maxX = (safeSrcW - 1).toFloat()
    val maxY = (safeSrcH - 1).toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            var accR = 0f
            var accG = 0f
            var accB = 0f
            var accA = 0f
            var maxAlpha = 0
            var brightestPixel = Color.TRANSPARENT
            var brightestScore = -1
            for (sy in samplePoints) {
                val sampleY = (srcTop + (srcBottom - srcTop) * sy).coerceIn(0f, maxY)
                val iy = sampleY.toInt()
                val rowOffset = iy * safeSrcW
                for (sx in samplePoints) {
                    val sampleX = (srcLeft + (srcRight - srcLeft) * sx).coerceIn(0f, maxX)
                    val ix = sampleX.toInt()
                    val pixel = srcPixels[rowOffset + ix]
                    val a = (pixel ushr 24) and 0xFF
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (a > 0) {
                        val brightness = r + g + b
                        if (a > maxAlpha || (a == maxAlpha && brightness > brightestScore)) {
                            maxAlpha = a
                            brightestScore = brightness
                            brightestPixel = pixel
                        }
                    }
                    accA += a.toFloat()
                    accR += r * a.toFloat()
                    accG += g * a.toFloat()
                    accB += b * a.toFloat()
                }
            }
            val avgA = accA / 9f
            val outA = avgA.roundToInt().coerceIn(0, 255)
            val outR: Int
            val outG: Int
            val outB: Int
            if (accA <= 0f) {
                outR = 0
                outG = 0
                outB = 0
            } else if (outA == 0 && maxAlpha > 0) {
                // 極細線の消失を防ぐため、代表ピクセルを採用する
                outR = (brightestPixel ushr 16) and 0xFF
                outG = (brightestPixel ushr 8) and 0xFF
                outB = brightestPixel and 0xFF
            } else {
                val invA = 1f / accA
                outR = (accR * invA).roundToInt().coerceIn(0, 255)
                outG = (accG * invA).roundToInt().coerceIn(0, 255)
                outB = (accB * invA).roundToInt().coerceIn(0, 255)
            }
            output[y * safeDstW + x] =
                ((if (outA == 0 && maxAlpha > 0) maxAlpha else outA) shl 24) or
                    (outR shl 16) or (outG shl 8) or outB
        }
    }
    return output
}

private fun downscaleNineSamplePremulAlphaWeighted(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    minAlphaCutoff: Int = 4,
): IntArray {
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val output = IntArray(safeDstW * safeDstH)
    val samplePoints = floatArrayOf(0.17f, 0.5f, 0.83f)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    val maxX = (safeSrcW - 1).toFloat()
    val maxY = (safeSrcH - 1).toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            var accR = 0f
            var accG = 0f
            var accB = 0f
            var accA = 0f
            var accW = 0f
            var maxAlpha = 0
            var maxR = 0
            var maxG = 0
            var maxB = 0
            for (sy in samplePoints) {
                val sampleY = (srcTop + (srcBottom - srcTop) * sy).coerceIn(0f, maxY)
                val iy = sampleY.toInt()
                val rowOffset = iy * safeSrcW
                for (sx in samplePoints) {
                    val sampleX = (srcLeft + (srcRight - srcLeft) * sx).coerceIn(0f, maxX)
                    val ix = sampleX.toInt()
                    val pixel = srcPixels[rowOffset + ix]
                    val a = (pixel ushr 24) and 0xFF
                    if (a < minAlphaCutoff) {
                        continue
                    }
                    val weight = a.toFloat() / 255f
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (a > maxAlpha) {
                        maxAlpha = a
                        maxR = r
                        maxG = g
                        maxB = b
                    }
                    accW += weight
                    accA += a * weight
                    accR += r * a * weight
                    accG += g * a * weight
                    accB += b * a * weight
                }
            }
            val outA: Int
            val outR: Int
            val outG: Int
            val outB: Int
            if (accW <= 0f || accA <= 0f) {
                outA = 0
                outR = 0
                outG = 0
                outB = 0
            } else {
                val avgA = (accA / accW).roundToInt().coerceIn(0, 255)
                outA = maxOf(avgA, maxAlpha)
                if (outA == maxAlpha && maxAlpha > 0) {
                    outR = maxR
                    outG = maxG
                    outB = maxB
                } else {
                    val invA = 1f / accA
                    outR = (accR * invA).roundToInt().coerceIn(0, 255)
                    outG = (accG * invA).roundToInt().coerceIn(0, 255)
                    outB = (accB * invA).roundToInt().coerceIn(0, 255)
                }
            }
            output[y * safeDstW + x] =
                (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }
    return output
}

private fun downscaleMultiStepAlphaWeightedPremul(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    stepFactor: Float,
    minAlphaCutoff: Int = 4,
    maxSteps: Int = 16,
): IntArray {
    var curW = srcW.coerceAtLeast(1)
    var curH = srcH.coerceAtLeast(1)
    var curPixels = srcPixels
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    var stepCount = 0
    while (curW > safeDstW || curH > safeDstH) {
        var nextW = maxOf(safeDstW, (curW * stepFactor).roundToInt())
        var nextH = maxOf(safeDstH, (curH * stepFactor).roundToInt())
        if (nextW == curW && curW > safeDstW) {
            nextW = curW - 1
        }
        if (nextH == curH && curH > safeDstH) {
            nextH = curH - 1
        }
        if (nextW >= curW && nextH >= curH) {
            break
        }
        stepCount += 1
        if (stepCount > maxSteps) {
            break
        }
        curPixels = downscaleNineSamplePremulAlphaWeighted(
            srcPixels = curPixels,
            srcW = curW,
            srcH = curH,
            dstW = nextW,
            dstH = nextH,
            minAlphaCutoff = minAlphaCutoff,
        )
        curW = nextW
        curH = nextH
    }
    return if (curW == safeDstW && curH == safeDstH) {
        curPixels
    } else {
        downscaleNineSamplePremulAlphaWeighted(
            srcPixels = curPixels,
            srcW = curW,
            srcH = curH,
            dstW = safeDstW,
            dstH = safeDstH,
            minAlphaCutoff = minAlphaCutoff,
        )
    }
}

private fun downscaleRegionMaxAlpha(
    selectionPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    preferDark: Boolean = false,
): IntArray {
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val outPixels = IntArray(safeDstW * safeDstH)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        // floor/ceilの混在でサンプル範囲の空を避ける
        var sy0 = floor(srcTop).toInt().coerceIn(0, safeSrcH - 1)
        var sy1 = (ceil(srcBottom).toInt() - 1).coerceIn(0, safeSrcH - 1)
        if (sy1 < sy0) {
            sy1 = sy0
        }
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            // floor/ceilの混在でサンプル範囲の空を避ける
            var sx0 = floor(srcLeft).toInt().coerceIn(0, safeSrcW - 1)
            var sx1 = (ceil(srcRight).toInt() - 1).coerceIn(0, safeSrcW - 1)
            if (sx1 < sx0) {
                sx1 = sx0
            }
            var bestPixel = Color.TRANSPARENT
            var bestAlpha = -1
            var bestBrightness = if (preferDark) Int.MAX_VALUE else -1
            // 縮小先の代表ピクセルは「最大alpha優先」、同点は明暗をモードで選ぶ
            for (sy in sy0..sy1) {
                val row = sy * safeSrcW
                for (sx in sx0..sx1) {
                    val pixel = selectionPixels[row + sx]
                    val alpha = (pixel ushr 24) and 0xFF
                    val brightness = ((pixel ushr 16) and 0xFF) +
                        ((pixel ushr 8) and 0xFF) +
                        (pixel and 0xFF)
                    val isBetterBrightness = if (preferDark) {
                        brightness < bestBrightness
                    } else {
                        brightness > bestBrightness
                    }
                    if (alpha > bestAlpha || (alpha == bestAlpha && isBetterBrightness)) {
                        bestAlpha = alpha
                        bestBrightness = brightness
                        bestPixel = pixel
                    }
                }
            }
            outPixels[y * safeDstW + x] = bestPixel
        }
    }
    return outPixels
}

private fun downscaleCenterSampleRect(
    selectionPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    minAlphaCutoff: Int = 4,
): IntArray {
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val output = IntArray(safeDstW * safeDstH)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    val maxX = safeSrcW - 1
    val maxY = safeSrcH - 1
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        val sampleY = ((srcTop + srcBottom) * 0.5f).toInt().coerceIn(0, maxY)
        val rowOffset = sampleY * safeSrcW
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            val sampleX = ((srcLeft + srcRight) * 0.5f).toInt().coerceIn(0, maxX)
            val pixel = selectionPixels[rowOffset + sampleX]
            val alpha = (pixel ushr 24) and 0xFF
            output[y * safeDstW + x] = if (alpha < minAlphaCutoff) {
                Color.TRANSPARENT
            } else {
                pixel
            }
        }
    }
    return output
}

private fun copySelectionPixels(src: Bitmap, rect: RectPx): IntArray {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val srcW = safeRect.w.coerceAtLeast(1)
    val srcH = safeRect.h.coerceAtLeast(1)
    val selectionPixels = IntArray(srcW * srcH)
    val rowBuffer = IntArray(srcW)
    // 選択範囲のオフセットを必ず反映して取得する（座標系ズレ防止）
    for (y in 0 until srcH) {
        src.getPixels(rowBuffer, 0, srcW, safeRect.x, safeRect.y + y, srcW, 1)
        System.arraycopy(rowBuffer, 0, selectionPixels, y * srcW, srcW)
    }
    return selectionPixels
}

fun resizeSelectionToMax64(
    src: Bitmap,
    selection: RectPx,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
    downscaleMode: ResizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep,
    pixelArtMethod: PixelArtStableMethod = PixelArtStableMethod.CenterSample,
): ResizeSelectionResult = resizeSelectionToMax(src, selection, 64, anchor, stepFactor, minAlphaCutoff, downscaleMode, pixelArtMethod)

// 96px APIと挙動を互換維持し、共通実装へ委譲する
fun resizeSelectionToMax96(
    src: Bitmap,
    selection: RectPx,
    maxSize: Int = 96,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
    downscaleMode: ResizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep,
    pixelArtMethod: PixelArtStableMethod = PixelArtStableMethod.CenterSample,
): ResizeSelectionResult = resizeSelectionToMax(src, selection, maxSize, anchor, stepFactor, minAlphaCutoff, downscaleMode, pixelArtMethod)

fun resizeSelectionToMax128(
    src: Bitmap,
    selection: RectPx,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
    downscaleMode: ResizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep,
    pixelArtMethod: PixelArtStableMethod = PixelArtStableMethod.CenterSample,
): ResizeSelectionResult = resizeSelectionToMax(src, selection, 128, anchor, stepFactor, minAlphaCutoff, downscaleMode, pixelArtMethod)

fun resizeSelectionToMax288(
    src: Bitmap,
    selection: RectPx,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
    downscaleMode: ResizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep,
    pixelArtMethod: PixelArtStableMethod = PixelArtStableMethod.CenterSample,
): ResizeSelectionResult = resizeSelectionToMax(src, selection, 288, anchor, stepFactor, minAlphaCutoff, downscaleMode, pixelArtMethod)

private fun resizeSelectionToMax(

    src: Bitmap,
    selection: RectPx,
    targetMaxPx: Int,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
    downscaleMode: ResizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep,
    pixelArtMethod: PixelArtStableMethod = PixelArtStableMethod.CenterSample,
): ResizeSelectionResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return ResizeSelectionResult(safeSrc, selection, false, "invalid bitmap")
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val maxDim = maxOf(safeSelection.w, safeSelection.h)
    val safeTargetMaxPx = targetMaxPx.coerceAtLeast(1)
    if (maxDim <= safeTargetMaxPx) {
        return ResizeSelectionResult(safeSrc, safeSelection, false, "already <= max")
    }
    val scale = safeTargetMaxPx.toFloat() / maxDim.toFloat()
    val dstW = (safeSelection.w * scale).roundToInt().coerceAtLeast(1)
    val dstH = (safeSelection.h * scale).roundToInt().coerceAtLeast(1)
    val pasteX = when (anchor) {
        ResizeAnchor.TopLeft -> safeSelection.x
        // 選択範囲の中心に合わせるため、縮小後サイズとの差分を半分だけずらす
        ResizeAnchor.Center -> safeSelection.x + (safeSelection.w - dstW) / 2
    }
    val pasteY = when (anchor) {
        ResizeAnchor.TopLeft -> safeSelection.y
        // 選択範囲の中心に合わせるため、縮小後サイズとの差分を半分だけずらす
        ResizeAnchor.Center -> safeSelection.y + (safeSelection.h - dstH) / 2
    }
    val newSelection = rectNormalizeClamp(RectPx.of(pasteX, pasteY, dstW, dstH), width, height)
    // clamp 後の座標に合わせて貼り付ける（座標ズレ防止）
    val dstX = newSelection.x
    val dstY = newSelection.y
    val selectionPixels = copySelectionPixels(safeSrc, safeSelection)

    val downscaledPixels = when (downscaleMode) {
        ResizeDownscaleMode.DefaultMultiStep -> {
            // 既存の細線保護を活かしつつ段階縮小で安定させる
            downscaleMultiStepAlphaWeightedPremul(
                srcPixels = selectionPixels,
                srcW = safeSelection.w,
                srcH = safeSelection.h,
                dstW = newSelection.w,
                dstH = newSelection.h,
                stepFactor = stepFactor,
                minAlphaCutoff = minAlphaCutoff,
            )
        }

        ResizeDownscaleMode.PixelArtStable -> {
            when (pixelArtMethod) {
                PixelArtStableMethod.CenterSample -> downscaleCenterSampleRect(
                    selectionPixels = selectionPixels,
                    srcW = safeSelection.w,
                    srcH = safeSelection.h,
                    dstW = newSelection.w,
                    dstH = newSelection.h,
                    minAlphaCutoff = minAlphaCutoff,
                )

                PixelArtStableMethod.DarkDominant -> downscaleRegionMaxAlpha(
                    selectionPixels = selectionPixels,
                    srcW = safeSelection.w,
                    srcH = safeSelection.h,
                    dstW = newSelection.w,
                    dstH = newSelection.h,
                    preferDark = true,
                )
            }
        }

        ResizeDownscaleMode.LegacyMaxAlpha -> downscaleRegionMaxAlpha(
            selectionPixels = selectionPixels,
            srcW = safeSelection.w,
            srcH = safeSelection.h,
            dstW = newSelection.w,
            dstH = newSelection.h,
            preferDark = false,
        )
    }
    val clipBitmap = Bitmap.createBitmap(newSelection.w, newSelection.h, Bitmap.Config.ARGB_8888)
    clipBitmap.setPixels(
        downscaledPixels,
        0,
        newSelection.w,
        0,
        0,
        newSelection.w,
        newSelection.h,
    )

    // 元の選択範囲をクリアしてから縮小結果を貼り付ける
    val cleared = clearTransparent(safeSrc, safeSelection)
    val output = cleared.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    canvas.drawBitmap(clipBitmap, dstX.toFloat(), dstY.toFloat(), null)
    val debugText = "scale=$scale new=${newSelection.w}x${newSelection.h} " +
        "step=$stepFactor cutoff=$minAlphaCutoff mode=$downscaleMode method=$pixelArtMethod"
    return ResizeSelectionResult(output, newSelection, true, debugText)
}

// Bitmap全体をグレースケールへ焼き込み変換した新しいBitmapを返す（元のBitmapは変更しない）
fun toGrayscale(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    if (safeSrc.width <= 0 || safeSrc.height <= 0) {
        return safeSrc
    }
    val width = safeSrc.width
    val height = safeSrc.height
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(size)
    for (index in 0 until size) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val gray = (299 * red + 587 * green + 114 * blue + 500) / 1000
        outPixels[index] = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体を大津の二値化で白黒変換した新しいBitmapを返す（元のBitmapは変更しない）
fun toBinarize(src: Bitmap, alphaThreshold: Int = BINARIZE_ALPHA_THRESHOLD): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)

    val otsu = otsuThresholdFromPixels(srcPixels, alphaThreshold)
    val threshold = if (otsu == null || otsu < BINARIZE_MIN_OTSU_THRESHOLD || otsu > BINARIZE_MAX_OTSU_THRESHOLD) {
        BINARIZE_FALLBACK_THRESHOLD
    } else {
        otsu
    }

    val outPixels = IntArray(size)
    for (index in 0 until size) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < alphaThreshold) {
            outPixels[index] = 0
            continue
        }

        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val luminance = (299 * red + 587 * green + 114 * blue + 500) / 1000
        outPixels[index] = if (luminance < threshold) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

private fun otsuThresholdFromPixels(pixels: IntArray, alphaThreshold: Int): Int? {
    val histogram = IntArray(256)
    var validPixelCount = 0
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < alphaThreshold) {
            continue
        }
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val luminance = (299 * red + 587 * green + 114 * blue + 500) / 1000
        histogram[luminance] += 1
        validPixelCount += 1
    }

    if (validPixelCount < BINARIZE_MIN_VALID_PIXELS) {
        return null
    }

    var sum = 0.0
    for (i in histogram.indices) {
        sum += i * histogram[i].toDouble()
    }

    var backgroundWeight = 0.0
    var backgroundSum = 0.0
    var bestVariance = -1.0
    var bestThreshold = 0
    val total = validPixelCount.toDouble()

    for (i in histogram.indices) {
        backgroundWeight += histogram[i].toDouble()
        if (backgroundWeight <= 0.0) {
            continue
        }

        val foregroundWeight = total - backgroundWeight
        if (foregroundWeight <= 0.0) {
            break
        }

        backgroundSum += i * histogram[i].toDouble()
        val backgroundMean = backgroundSum / backgroundWeight
        val foregroundMean = (sum - backgroundSum) / foregroundWeight
        val betweenClassVariance = backgroundWeight * foregroundWeight *
            (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)

        if (betweenClassVariance > bestVariance) {
            bestVariance = betweenClassVariance
            bestThreshold = i
        }
    }

    return bestThreshold
}

// Bitmap全体に8近傍ベースの外側1pxアウトラインを焼き込んだ新しいBitmapを返す（元のBitmapは変更しない）
fun addOutline(
    src: Bitmap,
    outlineColor: Int = android.graphics.Color.BLACK,
    thresholdAlpha: Int = 16,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val srcPixels = IntArray(width * height)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val neighborOffsets = arrayOf(
        intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        intArrayOf(-1, 0), intArrayOf(1, 0),
        intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1),
    )

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val alpha = (srcPixels[index] ushr 24) and 0xFF
            val isBody = alpha >= thresholdAlpha
            if (isBody) {
                continue
            }

            var hasBodyNeighbor = false
            for (offset in neighborOffsets) {
                val nx = x + offset[0]
                val ny = y + offset[1]
                if (nx !in 0 until width || ny !in 0 until height) {
                    continue
                }
                val nIndex = ny * width + nx
                val nAlpha = (srcPixels[nIndex] ushr 24) and 0xFF
                if (nAlpha >= thresholdAlpha) {
                    hasBodyNeighbor = true
                    break
                }
            }

            if (hasBodyNeighbor) {
                outPixels[index] = outlineColor
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体に「外側背景に接する境界のみ」1pxアウトラインを焼き込んだ新しいBitmapを返す（元のBitmapは変更しない）
fun addOuterOutline(
    src: Bitmap,
    outlineColor: Int = android.graphics.Color.BLACK,
    thresholdAlpha: Int = 16,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)

    val isOpaque = BooleanArray(size)
    for (index in 0 until size) {
        val alpha = (srcPixels[index] ushr 24) and 0xFF
        isOpaque[index] = alpha >= thresholdAlpha
    }

    val outside = BooleanArray(size)
    val queue = ArrayDeque<Int>()

    fun enqueueIfOutside(x: Int, y: Int) {
        val idx = y * width + x
        if (!isOpaque[idx] && !outside[idx]) {
            outside[idx] = true
            queue.addLast(idx)
        }
    }

    for (x in 0 until width) {
        enqueueIfOutside(x, 0)
        enqueueIfOutside(x, height - 1)
    }
    for (y in 0 until height) {
        enqueueIfOutside(0, y)
        enqueueIfOutside(width - 1, y)
    }

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        val x = idx % width
        val y = idx / width

        if (x > 0) {
            val left = idx - 1
            if (!isOpaque[left] && !outside[left]) {
                outside[left] = true
                queue.addLast(left)
            }
        }
        if (x < width - 1) {
            val right = idx + 1
            if (!isOpaque[right] && !outside[right]) {
                outside[right] = true
                queue.addLast(right)
            }
        }
        if (y > 0) {
            val top = idx - width
            if (!isOpaque[top] && !outside[top]) {
                outside[top] = true
                queue.addLast(top)
            }
        }
        if (y < height - 1) {
            val bottom = idx + width
            if (!isOpaque[bottom] && !outside[bottom]) {
                outside[bottom] = true
                queue.addLast(bottom)
            }
        }
    }

    val outPixels = srcPixels.copyOf()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            if (!outside[idx]) {
                continue
            }

            var hasOpaqueNeighbor = false
            for (ny in (y - 1)..(y + 1)) {
                if (ny !in 0 until height) continue
                for (nx in (x - 1)..(x + 1)) {
                    if (nx !in 0 until width) continue
                    if (nx == x && ny == y) continue
                    val neighborIdx = ny * width + nx
                    if (isOpaque[neighborIdx]) {
                        hasOpaqueNeighbor = true
                        break
                    }
                }
                if (hasOpaqueNeighbor) {
                    break
                }
            }

            if (hasOpaqueNeighbor) {
                outPixels[idx] = outlineColor
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体から外周に接する背景領域を透明化した新しいBitmapを返す（元のBitmapは変更しない）
fun clearEdgeConnectedBackground(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val bgSample = sampleEdgeBackgroundRgb(srcPixels, width, height)
    val bgR = bgSample?.first ?: 0
    val bgG = bgSample?.second ?: 0
    val bgB = bgSample?.third ?: 0
    val transparentOnly = bgSample == null

    val visited = BooleanArray(size)
    val queue = ArrayDeque<Int>()

    fun tryEnqueue(index: Int) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        val isBackground = if (transparentOnly) {
            alpha == 0
        } else {
            isBackgroundLike(pixel, bgR, bgG, bgB)
        }
        if (!visited[index] && isBackground) {
            visited[index] = true
            queue.addLast(index)
        }
    }

    for (x in 0 until width) {
        tryEnqueue(x)
        tryEnqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
        tryEnqueue(y * width)
        tryEnqueue(y * width + (width - 1))
    }

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        val x = index % width
        val y = index / width

        outPixels[index] = srcPixels[index] and 0x00FFFFFF

        if (x > 0) tryEnqueue(index - 1)
        if (x < width - 1) tryEnqueue(index + 1)
        if (y > 0) tryEnqueue(index - width)
        if (y < height - 1) tryEnqueue(index + width)
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// 選択矩形内の代表点から連結成分を透明化した新しいBitmapを返す（元のBitmapは変更しない）
fun clearConnectedRegionFromSelection(
    src: Bitmap,
    selection: RectPx,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    var seedIndex = -1
    val endY = safeSelection.y + safeSelection.h
    val endX = safeSelection.x + safeSelection.w
    for (y in safeSelection.y until endY) {
        for (x in safeSelection.x until endX) {
            val index = y * width + x
            val alpha = (srcPixels[index] ushr 24) and 0xFF
            if (alpha > 0) {
                seedIndex = index
                break
            }
        }
        if (seedIndex >= 0) {
            break
        }
    }

    if (seedIndex < 0) {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    val seedPixel = srcPixels[seedIndex]
    val seedR = (seedPixel ushr 16) and 0xFF
    val seedG = (seedPixel ushr 8) and 0xFF
    val seedB = seedPixel and 0xFF

    val visited = BooleanArray(size)
    val queue = ArrayDeque<Int>()
    visited[seedIndex] = true
    queue.addLast(seedIndex)

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        val x = index % width
        val y = index / width

        outPixels[index] = srcPixels[index] and 0x00FFFFFF

        if (x > 0) enqueueIfRegionMatch(index - 1, srcPixels, visited, queue, seedR, seedG, seedB)
        if (x < width - 1) enqueueIfRegionMatch(index + 1, srcPixels, visited, queue, seedR, seedG, seedB)
        if (y > 0) enqueueIfRegionMatch(index - width, srcPixels, visited, queue, seedR, seedG, seedB)
        if (y < height - 1) enqueueIfRegionMatch(index + width, srcPixels, visited, queue, seedR, seedG, seedB)
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// 選択矩形内の透明ピクセルをseedに4近傍で連結探索し、到達領域を白で塗る（元のBitmapは変更しない）
enum class FillRegionTransparentStatus {
    APPLIED,
    NO_TRANSPARENT_PIXELS_IN_SELECTION,
    ABORTED_TOO_LARGE,
}

data class FillRegionTransparentResult(
    val bitmap: Bitmap,
    val status: FillRegionTransparentStatus,
)

fun fillRegionFromTransparentSeeds(
    src: Bitmap,
    selection: RectPx,
    maxFillPixels: Int = selection.w * selection.h,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): FillRegionTransparentResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return FillRegionTransparentResult(
            bitmap = safeSrc,
            status = FillRegionTransparentStatus.NO_TRANSPARENT_PIXELS_IN_SELECTION,
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val visited = BooleanArray(size)
    val queue = IntArray(size)
    val white = 0xFFFFFFFF.toInt()
    val selectionArea = safeSelection.w * safeSelection.h
    val selectionBasedLimit = selectionArea.coerceAtLeast(1)
        .coerceAtMost(FILL_REGION_ABSOLUTE_MAX_PIXELS)
    val fillLimit = maxFillPixels.coerceAtLeast(1)
        .coerceAtMost(selectionBasedLimit)

    fun isTransparent(index: Int): Boolean {
        val alpha = (srcPixels[index] ushr 24) and 0xFF
        return alpha < transparentAlphaThreshold
    }

    val sy = safeSelection.y
    val ey = safeSelection.y + safeSelection.h
    val sx = safeSelection.x
    val ex = safeSelection.x + safeSelection.w

    var head = 0
    var tail = 0
    fun enqueueSeed(x: Int, y: Int) {
        val seedIndex = y * width + x
        if (visited[seedIndex] || !isTransparent(seedIndex)) {
            return
        }
        visited[seedIndex] = true
        queue[tail++] = seedIndex
    }

    for (x in sx until ex) {
        enqueueSeed(x, sy)
        if (ey - 1 != sy) {
            enqueueSeed(x, ey - 1)
        }
    }
    for (y in sy until ey) {
        enqueueSeed(sx, y)
        if (ex - 1 != sx) {
            enqueueSeed(ex - 1, y)
        }
    }

    if (tail == 0) {
        return FillRegionTransparentResult(
            bitmap = safeSrc,
            status = FillRegionTransparentStatus.NO_TRANSPARENT_PIXELS_IN_SELECTION,
        )
    }

    var filledCount = 0
    while (head < tail) {
        val index = queue[head++]
        outPixels[index] = white
        filledCount += 1
        if (filledCount > fillLimit) {
            return FillRegionTransparentResult(
                bitmap = safeSrc,
                status = FillRegionTransparentStatus.ABORTED_TOO_LARGE,
            )
        }

        val px = index % width
        val py = index / width

        if (px > sx) {
            val left = index - 1
            if (!visited[left] && isTransparent(left)) {
                visited[left] = true
                queue[tail++] = left
            }
        }
        if (px + 1 < ex) {
            val right = index + 1
            if (!visited[right] && isTransparent(right)) {
                visited[right] = true
                queue[tail++] = right
            }
        }
        if (py > sy) {
            val up = index - width
            if (!visited[up] && isTransparent(up)) {
                visited[up] = true
                queue[tail++] = up
            }
        }
        if (py + 1 < ey) {
            val down = index + width
            if (!visited[down] && isTransparent(down)) {
                visited[down] = true
                queue[tail++] = down
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return FillRegionTransparentResult(
        bitmap = output,
        status = FillRegionTransparentStatus.APPLIED,
    )
}

fun fillConnectedToWhite(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
    rgbTolerance: Int = FILL_CONNECTED_RGB_TOLERANCE,
): FillConnectedResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return FillConnectedResult(
            bitmap = safeSrc,
            filled = 0,
            aborted = false,
            mode = Mode.Alpha,
            debugText = "Fill: mode=alpha T=0 thr=$transparentAlphaThreshold filled=0",
            seedType = FillConnectedSeedType.None,
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val imageArea = width * height
    val maxFillPixels = minOf(imageArea, FILL_REGION_ABSOLUTE_MAX_PIXELS).coerceAtLeast(1)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()
    val visited = BooleanArray(size)
    val queue = IntArray(size)

    val sx = safeSelection.x
    val sy = safeSelection.y
    val ex = safeSelection.x + safeSelection.w
    val ey = safeSelection.y + safeSelection.h

    var transparentCount = 0
    var rgbCount = 0
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    for (y in sy until ey) {
        for (x in sx until ex) {
            val pixel = srcPixels[y * width + x]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < transparentAlphaThreshold) {
                transparentCount += 1
            } else {
                sumR += (pixel ushr 16) and 0xFF
                sumG += (pixel ushr 8) and 0xFF
                sumB += pixel and 0xFF
                rgbCount += 1
            }
        }
    }

    val mode = if (transparentCount > 0) Mode.Alpha else Mode.Rgb
    val avgR = if (rgbCount > 0) (sumR / rgbCount).toInt() else 0
    val avgG = if (rgbCount > 0) (sumG / rgbCount).toInt() else 0
    val avgB = if (rgbCount > 0) (sumB / rgbCount).toInt() else 0

    fun isTarget(pixel: Int): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        return if (mode == Mode.Alpha) {
            alpha < transparentAlphaThreshold
        } else {
            if (alpha < transparentAlphaThreshold) return false
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            kotlin.math.abs(red - avgR) + kotlin.math.abs(green - avgG) + kotlin.math.abs(blue - avgB) <= rgbTolerance
        }
    }

    var head = 0
    var tail = 0
    var seedType = FillConnectedSeedType.None
    for (y in sy until ey) {
        for (x in sx until ex) {
            val index = y * width + x
            if (!visited[index] && isTarget(srcPixels[index])) {
                visited[index] = true
                queue[tail++] = index
                if (seedType == FillConnectedSeedType.None) {
                    seedType = resolveFillConnectedSeedType(srcPixels[index], transparentAlphaThreshold)
                }
            }
        }
    }

    if (tail == 0) {
        val debugText = if (mode == Mode.Alpha) {
            "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=0 limit=$maxFillPixels"
        } else {
            "Fill: mode=rgb tol=$rgbTolerance filled=0 limit=$maxFillPixels"
        }
        return FillConnectedResult(safeSrc, 0, false, mode, debugText, FillConnectedSeedType.None)
    }

    val white = 0xFFFFFFFF.toInt()
    var filledCount = 0
    while (head < tail) {
        val index = queue[head++]
        outPixels[index] = white
        filledCount += 1
        if (filledCount > maxFillPixels) {
            val debugText = if (mode == Mode.Alpha) {
                "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=$filledCount limit=$maxFillPixels"
            } else {
                "Fill: mode=rgb tol=$rgbTolerance filled=$filledCount limit=$maxFillPixels"
            }
            return FillConnectedResult(safeSrc, filledCount, true, mode, debugText, seedType)
        }

        val px = index % width
        val py = index / width
        if (px > 0) {
            val left = index - 1
            if (!visited[left] && isTarget(srcPixels[left])) {
                visited[left] = true
                queue[tail++] = left
            }
        }
        if (px < width - 1) {
            val right = index + 1
            if (!visited[right] && isTarget(srcPixels[right])) {
                visited[right] = true
                queue[tail++] = right
            }
        }
        if (py > 0) {
            val up = index - width
            if (!visited[up] && isTarget(srcPixels[up])) {
                visited[up] = true
                queue[tail++] = up
            }
        }
        if (py < height - 1) {
            val down = index + width
            if (!visited[down] && isTarget(srcPixels[down])) {
                visited[down] = true
                queue[tail++] = down
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    val debugText = if (mode == Mode.Alpha) {
        "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=$filledCount limit=$maxFillPixels"
    } else {
        "Fill: mode=rgb tol=$rgbTolerance filled=$filledCount limit=$maxFillPixels"
    }
    return FillConnectedResult(output, filledCount, false, mode, debugText, seedType)
}

private fun resolveFillConnectedSeedType(
    pixel: Int,
    transparentAlphaThreshold: Int,
): FillConnectedSeedType {
    val alpha = (pixel ushr 24) and 0xFF
    if (alpha < transparentAlphaThreshold) {
        return FillConnectedSeedType.Transparent
    }
    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    return when {
        red <= 16 && green <= 16 && blue <= 16 -> FillConnectedSeedType.Black
        red >= 239 && green >= 239 && blue >= 239 -> FillConnectedSeedType.White
        else -> FillConnectedSeedType.Other
    }
}

private fun sampleEdgeBackgroundRgb(
    pixels: IntArray,
    width: Int,
    height: Int,
): Triple<Int, Int, Int>? {
    val sampleIndices = linkedSetOf<Int>()
    for (x in 0 until width) {
        sampleIndices.add(x)
        sampleIndices.add((height - 1) * width + x)
    }
    for (y in 0 until height) {
        sampleIndices.add(y * width)
        sampleIndices.add(y * width + (width - 1))
    }

    val reds = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)
    val greens = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)
    val blues = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)

    for (index in sampleIndices) {
        val pixel = pixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha <= 0) {
            continue
        }
        reds.add((pixel ushr 16) and 0xFF)
        greens.add((pixel ushr 8) and 0xFF)
        blues.add(pixel and 0xFF)
        if (reds.size >= CLEAR_BG_EDGE_SAMPLE_LIMIT) {
            break
        }
    }

    if (reds.isEmpty()) {
        return null
    }

    reds.sort()
    greens.sort()
    blues.sort()
    val mid = reds.size / 2
    return Triple(reds[mid], greens[mid], blues[mid])
}

private fun isBackgroundLike(pixel: Int, bgR: Int, bgG: Int, bgB: Int): Boolean {
    val alpha = (pixel ushr 24) and 0xFF
    if (alpha == 0) {
        return true
    }
    if (alpha < CLEAR_BG_MIN_ALPHA) {
        return false
    }

    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    val colorDistance = kotlin.math.abs(red - bgR) +
        kotlin.math.abs(green - bgG) +
        kotlin.math.abs(blue - bgB)
    return colorDistance <= CLEAR_BG_COLOR_DISTANCE_THRESHOLD
}

private fun enqueueIfRegionMatch(
    index: Int,
    srcPixels: IntArray,
    visited: BooleanArray,
    queue: ArrayDeque<Int>,
    seedR: Int,
    seedG: Int,
    seedB: Int,
) {
    if (visited[index]) {
        return
    }
    val pixel = srcPixels[index]
    val alpha = (pixel ushr 24) and 0xFF
    if (alpha <= 0) {
        return
    }

    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    val colorDistance = kotlin.math.abs(red - seedR) +
        kotlin.math.abs(green - seedG) +
        kotlin.math.abs(blue - seedB)
    if (colorDistance > CLEAR_REGION_COLOR_DISTANCE_THRESHOLD) {
        return
    }

    visited[index] = true
    queue.addLast(index)
}
