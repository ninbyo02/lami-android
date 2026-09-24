package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message

internal fun computeLatestUserAnchor(messages: List<Message>): Int {
    if (messages.isEmpty()) {
        return 0
    }
    val lastUser = messages.indexOfLast { it.isSendbyMe }
    return if (lastUser >= 0) {
        lastUser
    } else {
        messages.lastIndex
    }
}

internal fun isStopCancellationLikeMessage(message: String?): Boolean {
    val text = message?.lowercase().orEmpty()
    return "socket closed" in text ||
        "software caused connection abort" in text ||
        "canceled" in text ||
        "cancelled" in text ||
        "stream was reset" in text
}
