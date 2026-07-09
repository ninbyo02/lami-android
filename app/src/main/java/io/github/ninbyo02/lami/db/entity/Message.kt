package io.github.ninbyo02.lami.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_table")
data class Message(
    @PrimaryKey(autoGenerate = true) val messageID: Int = 0,
    val chatId: Int,
    val message: String,
    val isSendbyMe: Boolean,
    val attachmentUriString: String? = null,
    val attachmentUriStringsJson: String? = null,
    val completionTokens: Int? = null,
    val generationTimeMs: Long? = null,
    val generationDurationNs: Long? = null,
    val evalDurationNs: Long? = null,
    val loadDurationNs: Long? = null,
    val promptEvalDurationNs: Long? = null,
    val modelName: String? = null,
    val inputTokens: Int? = null,
    val totalTokens: Int? = null,
    val tokensPerSecond: Double? = null,
    val charsPerSecond: Double? = null,
    val tokenCountMode: String? = null,
    val inferenceNotes: String? = null,
    val inferenceTimeSec: Double? = null,
    val decodeDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    val finishReason: String? = null,
    val localSourceSummary: String? = null,
    val timeToFirstTokenMs: Long? = null,
    val imageInputCount: Int? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
