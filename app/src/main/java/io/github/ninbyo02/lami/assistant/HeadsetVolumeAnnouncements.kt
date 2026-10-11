package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

enum class HeadsetVolumeFeedbackPhase { NORMAL, RELEASE_CUE, SETTLED, FAILED }

data class HeadsetVolumeAnnouncement(
    val percent: Int,
    val createdAtMs: Long,
    val phase: HeadsetVolumeFeedbackPhase = HeadsetVolumeFeedbackPhase.NORMAL,
) {
    val text: String get() = when (phase) {
        HeadsetVolumeFeedbackPhase.RELEASE_CUE -> "音量。"
        HeadsetVolumeFeedbackPhase.SETTLED -> "${percent}パーセント。"
        HeadsetVolumeFeedbackPhase.FAILED -> "音量を確認できませんでした。"
        HeadsetVolumeFeedbackPhase.NORMAL -> "音量、${percent}パーセント。"
    }
}

/** No replay: volume feedback belongs only to the currently visible chat. */
object HeadsetVolumeAnnouncements {
    @Volatile var chatForeground: Boolean = false
    @Volatile var conversationActive: Boolean = false
    @Volatile var assistantWorkActive: Boolean = false
    @Volatile var lastVolumeSpeechEndMs: Long = -1L
    private val flow = MutableSharedFlow<HeadsetVolumeAnnouncement>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = flow.asSharedFlow()

    fun publish(announcement: HeadsetVolumeAnnouncement) {
        flow.tryEmit(announcement)
    }
}

internal class IdleVolumeAnnouncementPolicy {
    private var previous: IdleHeadsetVolumeSnapshot? = null
    private var announceAfterMs: Long? = null

    fun update(
        snapshot: IdleHeadsetVolumeSnapshot,
        minVolume: Int,
        maxVolume: Int,
        nowMs: Long,
    ): HeadsetVolumeAnnouncement? {
        val old = previous
        previous = snapshot
        if (!snapshot.eligible || snapshot.routeId == null || old?.eligible != true ||
            old.routeId != snapshot.routeId
        ) {
            announceAfterMs = null
            return null
        }
        if (old.mediaVolume != snapshot.mediaVolume) {
            announceAfterMs = nowMs + 1_400
        }
        val deadline = announceAfterMs ?: return null
        if (nowMs < deadline) return null
        announceAfterMs = null
        if (maxVolume <= minVolume) return null
        val percent = ((snapshot.mediaVolume - minVolume).toDouble() /
            (maxVolume - minVolume) * 100).roundToInt().coerceIn(0, 100)
        return HeadsetVolumeAnnouncement(
            ((percent + 5) / 10 * 10).coerceIn(0, ASSISTANT_VOLUME_MAX_TIER * 10), nowMs,
        )
    }

    fun reset() {
        previous = null
        announceAfterMs = null
    }
}
