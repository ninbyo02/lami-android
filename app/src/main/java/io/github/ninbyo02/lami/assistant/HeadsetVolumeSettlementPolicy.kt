package io.github.ninbyo02.lami.assistant

import kotlin.math.abs

internal sealed interface HeadsetVolumeSettlementAction {
    data class Apply(val target: Int, val repair: Boolean) : HeadsetVolumeSettlementAction
    data class Confirm(val target: Int, val actual: Int) : HeadsetVolumeSettlementAction
    data class Cancel(val reason: String) : HeadsetVolumeSettlementAction
}

/** Wait for release before setting volume. At most one repair; never a polling write loop. */
internal class HeadsetVolumeSettlementPolicy {
    private data class Pending(
        val routeId: Int,
        val target: Int,
        val startedAt: Long,
        var previous: IdleHeadsetVolumeSnapshot,
        var quietSince: Long,
        var applied: Boolean = false,
        var repaired: Boolean = false,
        var awaitingApply: Boolean = false,
    )

    private var pending: Pending? = null
    private var lastConfirmedRoute: Int? = null
    private var lastConfirmedAt: Long? = null
    val isActive: Boolean get() = pending != null
    var releaseCueRequired: Boolean = true
        private set

    fun begin(snapshot: IdleHeadsetVolumeSnapshot, target: Int, nowMs: Long): Boolean {
        val route = snapshot.routeId ?: return false
        if (pending != null || !snapshot.eligible) return false
        // Android does not expose its volume-dialog visibility. Use a bounded
        // same-route follow-up window instead, without relaxing the settling checks.
        val elapsed = lastConfirmedAt?.let { nowMs - it }
        releaseCueRequired = route != lastConfirmedRoute || elapsed == null || elapsed !in 0 until 5_000L
        pending = Pending(route, target, nowMs, snapshot, nowMs)
        return true
    }

    fun update(
        snapshot: IdleHeadsetVolumeSnapshot,
        feedbackSpeaking: Boolean,
        interrupted: Boolean,
        maxVolume: Int,
        nowMs: Long,
    ): HeadsetVolumeSettlementAction? {
        val state = pending ?: return null
        if (snapshot.routeId != state.routeId) return cancel("route_changed")
        if (interrupted) return cancel("assistant_or_call_started")
        if (nowMs - state.startedAt >= 8_000) return cancel("timeout")
        if (snapshot.voiceVolume != state.previous.voiceVolume ||
            snapshot.mediaVolume != state.previous.mediaVolume) state.quietSince = nowMs
        state.previous = snapshot
        if (feedbackSpeaking || !snapshot.eligible || state.awaitingApply) return null
        val quietFor = nowMs - state.quietSince
        if (!state.applied) {
            if (quietFor < 900) return null
            state.awaitingApply = true
            return HeadsetVolumeSettlementAction.Apply(state.target, repair = false)
        }
        if (quietFor < 350) return null
        // Allow only the phone/headset's half-step rounding, not another native button step.
        if (abs(snapshot.mediaVolume - state.target) <= maxVolume / 30) {
            lastConfirmedRoute = state.routeId
            lastConfirmedAt = nowMs
            pending = null
            return HeadsetVolumeSettlementAction.Confirm(state.target, snapshot.mediaVolume)
        }
        if (quietFor < 1_200) return null
        if (state.repaired) return cancel("volume_did_not_settle")
        state.awaitingApply = true
        return HeadsetVolumeSettlementAction.Apply(state.target, repair = true)
    }

    fun applied(actualVolume: Int, nowMs: Long, repair: Boolean) {
        val state = pending ?: return
        state.previous = state.previous.copy(mediaVolume = actualVolume)
        state.quietSince = nowMs
        state.applied = true
        state.repaired = repair
        state.awaitingApply = false
    }

    private fun cancel(reason: String): HeadsetVolumeSettlementAction.Cancel {
        reset()
        return HeadsetVolumeSettlementAction.Cancel(reason)
    }

    fun reset() {
        pending = null
        lastConfirmedRoute = null
        lastConfirmedAt = null
        releaseCueRequired = true
    }
}
