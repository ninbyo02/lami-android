package com.sonusid.ollama.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class BoxPosition(
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class SpriteSheetConfig(
    val rows: Int,
    val cols: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val boxes: List<BoxPosition>,
) {
    fun toJson(gson: Gson = Gson()): String = gson.toJson(this)

    fun validate(): String? {
        if (rows <= 0) return "rows は1以上にしてください"
        if (cols <= 0) return "cols は1以上にしてください"
        if (frameWidth <= 0 || frameHeight <= 0) {
            return "frameWidth と frameHeight は1以上の整数にしてください"
        }
        if (boxes.size != rows * cols) {
            return "boxes の数は rows×cols (${rows * cols}) と一致させてください"
        }
        val seenFrames = mutableSetOf<Int>()
        boxes.forEach { box ->
            if (box.frameIndex !in 0 until (rows * cols)) {
                return "frameIndex は 0 以上 ${rows * cols - 1} 以下にしてください"
            }
            if (!seenFrames.add(box.frameIndex)) {
                return "frameIndex ${box.frameIndex} が重複しています"
            }
            if (box.width <= 0 || box.height <= 0) {
                return "各 box の width と height は1以上にしてください"
            }
            if (box.x < 0 || box.y < 0) {
                return "各 box の x, y は0以上にしてください"
            }
        }
        return null
    }

    companion object {
        fun default3x3(frameSize: Int = 96): SpriteSheetConfig {
            val rows = 3
            val cols = 3
            val frameWidth = frameSize
            val frameHeight = frameSize
            val boxes = List(rows * cols) { index ->
                val row = index / cols
                val col = index % cols
                BoxPosition(
                    frameIndex = index,
                    x = col * frameWidth,
                    y = row * frameHeight,
                    width = frameWidth,
                    height = frameHeight,
                )
            }
            return SpriteSheetConfig(
                rows = rows,
                cols = cols,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                boxes = boxes,
            )
        }

        fun fromJson(json: String, gson: Gson = Gson()): SpriteSheetConfig? {
            return try {
                gson.fromJson(json, SpriteSheetConfig::class.java)
            } catch (ex: JsonSyntaxException) {
                null
            }
        }
    }
}
