package io.github.ninbyo02.lami.assistant

import kotlin.math.abs

internal data class IdleHeadsetVolumeSnapshot(
    val routeId: Int?,
    val eligible: Boolean,
    val voiceVolume: Int,
    val mediaVolume: Int,
)

internal fun acceptsIdleHeadsetInput(
    musicActive: Boolean,
    assistantBusy: Boolean,
    lastVolumeSpeechEndMs: Long,
    nowMs: Long,
): Boolean {
    if (!musicActive) return true
    return !assistantBusy && lastVolumeSpeechEndMs >= 0 &&
        nowMs - lastVolumeSpeechEndMs in 0L..600L
}

/** One step per observed burst, anchored to LAMI's last accepted level, not headset drift. */
internal class IdleHeadsetVolumePolicy {
    private var previous: IdleHeadsetVolumeSnapshot? = null
    private var routeSince: Long? = null
    private var lastInputAt: Long? = null
    private var logicalMediaVolume: Int? = null
    private var lastAppliedMediaVolume: Int? = null

    fun update(
        snapshot: IdleHeadsetVolumeSnapshot,
        nowMs: Long,
        maxMediaVolume: Int,
        conversationActive: Boolean = false,
    ): Int? {
        val old = previous
        previous = snapshot
        if (old == null || snapshot.routeId == null || old.routeId != snapshot.routeId) {
            routeSince = nowMs
            lastInputAt = null
            logicalMediaVolume = snapshot.mediaVolume
            lastAppliedMediaVolume = null
            return null
        }
        val voiceDelta = snapshot.voiceVolume - old.voiceVolume
        val mediaDelta = snapshot.mediaVolume - old.mediaVolume
        if (voiceDelta == 0 && mediaDelta == 0) return null
        // This phone reports 150 media units, but AVRCP rounds our 15-unit steps
        // back to 10-unit boundaries. A small echo is not another button press.
        val echoTolerance = maxMediaVolume / 30
        val mediaEcho = lastAppliedMediaVolume?.let {
            abs(mediaDelta) <= echoTolerance && abs(snapshot.mediaVolume - it) <= echoTolerance
        } == true
        if (voiceDelta == 0 && mediaEcho) return null
        val newBurst = lastInputAt?.let { nowMs - it >= 1_200 } != false
        // Track repeats even during speech, so its end cannot rearm a held button.
        lastInputAt = nowMs
        if (!newBurst || !snapshot.eligible || conversationActive ||
            nowMs - (routeSince ?: nowMs) < 1_000) return null
        val direction = if (voiceDelta != 0) voiceDelta.sign() else mediaDelta.sign()
        val target = nextAssistantMediaVolume(
            logicalMediaVolume ?: old.mediaVolume, direction, maxMediaVolume,
        )
        logicalMediaVolume = target
        return target
    }

    fun acknowledgeMediaAdjustment(mediaVolume: Int) {
        previous = previous?.copy(mediaVolume = mediaVolume)
        logicalMediaVolume = mediaVolume
        lastAppliedMediaVolume = mediaVolume
    }

    fun commandBaseline(actualMediaVolume: Int): Int = logicalMediaVolume ?: actualMediaVolume

    private fun Int.sign(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    fun reset() {
        previous = null
        routeSince = null
        lastInputAt = null
        logicalMediaVolume = null
        lastAppliedMediaVolume = null
    }
}
