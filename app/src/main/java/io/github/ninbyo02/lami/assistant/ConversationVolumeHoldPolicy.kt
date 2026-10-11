package io.github.ninbyo02.lami.assistant

/** Restores the pre-conversation volume until playback ends and headset input settles. */
internal class ConversationVolumeHoldPolicy {
    private var previous: IdleHeadsetVolumeSnapshot? = null
    private var heldVolume: Int? = null
    private var quietSinceMs: Long? = null
    val isHolding: Boolean get() = heldVolume != null
    var releasedOnLastUpdate: Boolean = false
        private set

    fun target(
        snapshot: IdleHeadsetVolumeSnapshot,
        conversationActive: Boolean,
        capVolume: Int,
        nowMs: Long,
    ): Int? {
        releasedOnLastUpdate = false
        if (snapshot.routeId == null) {
            reset()
            return null
        }
        val old = previous
        if (conversationActive && heldVolume == null) {
            heldVolume = (old?.takeIf { it.routeId == snapshot.routeId }?.mediaVolume
                ?: snapshot.mediaVolume).coerceAtMost(capVolume)
            quietSinceMs = nowMs
        }
        val target = heldVolume
        if (target != null) {
            val inputChanged = old != null && old.routeId == snapshot.routeId &&
                (old.mediaVolume != snapshot.mediaVolume || old.voiceVolume != snapshot.voiceVolume)
            if (inputChanged) quietSinceMs = nowMs
            if (conversationActive) {
                if (quietSinceMs == null) quietSinceMs = nowMs
            } else {
                if (quietSinceMs == null) quietSinceMs = nowMs
                if (nowMs - (quietSinceMs ?: nowMs) >= 900) {
                    heldVolume = null
                    quietSinceMs = null
                    previous = snapshot
                    releasedOnLastUpdate = true
                    return null
                }
            }
        }
        previous = snapshot
        return target
    }

    fun acknowledgeMediaAdjustment(volume: Int) {
        previous = previous?.copy(mediaVolume = volume)
    }

    fun noteKeyInput(nowMs: Long) {
        if (heldVolume != null) quietSinceMs = nowMs
    }

    fun reset() {
        previous = null
        heldVolume = null
        quietSinceMs = null
        releasedOnLastUpdate = false
    }
}
