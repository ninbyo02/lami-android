package io.github.ninbyo02.lami.ui.screens.home

internal fun failureStageForOfficialReason(reasonCode: String): String {
    return when (reasonCode) {
        "conversation_create_failed" -> "conversation-create"
        "flow_collect_failed" -> "streaming-callback"
        "send_message_async_missing",
        "send_message_missing",
        "message_extract_failed",
        "no_partial_emitted",
        "blank_response",
        -> "generate-response"
        else -> "unknown"
    }
}
