package io.github.ninbyo02.lami.db.entity

/**
 * Persisted lifecycle values for assistant messages.
 *
 * These remain strings in Room so the database is readable across app versions
 * without a converter-specific migration.
 */
object MessageStatus {
    const val PENDING = "PENDING"
    const val GENERATING = "GENERATING"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val FAILED = "FAILED"
    const val INTERRUPTED = "INTERRUPTED"

    val IN_FLIGHT = setOf(PENDING, GENERATING)
    val TERMINAL = setOf(COMPLETED, CANCELLED, FAILED, INTERRUPTED)
}

object MessageErrorCode {
    const val USER_CANCELLED = "USER_CANCELLED"
    const val GENERATION_FAILED = "GENERATION_FAILED"
    const val PROCESS_INTERRUPTED = "PROCESS_INTERRUPTED"
}
