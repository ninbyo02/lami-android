package io.github.ninbyo02.lami.ui.screens.home

internal data class RunCloseTargetOutcome(
    val label: String,
    val targetClassName: String?,
    val strategy: String?,
    val status: String,
    val errorClassName: String?,
    val message: String?,
)

internal data class RunCloseLifecycleSummary(
    val path: String,
    val successReturned: Boolean,
    val engineOutcome: RunCloseTargetOutcome? = null,
    val conversationOutcome: RunCloseTargetOutcome? = null,
    val sessionOutcome: RunCloseTargetOutcome? = null,
    val inferenceOutcome: RunCloseTargetOutcome? = null,
    val notes: String? = null,
)

internal fun closeQuietly(
    target: Any?,
    appendTrace: ((String) -> Unit)? = null,
) {
    tryCloseWithOutcome(
        label = "target",
        target = target,
        appendTrace = appendTrace,
        path = null,
    )
}

internal fun tryCloseWithOutcome(
    label: String,
    target: Any?,
    appendTrace: ((String) -> Unit)? = null,
    path: String? = null,
): RunCloseTargetOutcome {
    val targetClass = target?.javaClass?.name
    if (target == null) {
        val outcome = RunCloseTargetOutcome(
            label = label,
            targetClassName = targetClass,
            strategy = null,
            status = "none",
            errorClassName = null,
            message = null,
        )
        appendTrace?.let { trace ->
            path?.let { emitCloseSummaryTrace(trace, it, outcome) }
        }
        return outcome
    }
    if (target is AutoCloseable) {
        return runCatching { target.close() }
            .fold(
                onSuccess = {
                    runCatching {
                        appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=AutoCloseable.close success")
                    }
                    val outcome = RunCloseTargetOutcome(
                        label = label,
                        targetClassName = targetClass,
                        status = "success",
                        strategy = "AutoCloseable.close",
                        errorClassName = null,
                        message = null,
                    )
                    appendTrace?.let { trace ->
                        path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                    }
                    outcome
                },
                onFailure = { throwable ->
                    runCatching {
                        appendTrace?.invoke(
                            "UPSTREAM closeQuietly targetClass=$targetClass strategy=AutoCloseable.close failed ${throwable.javaClass.simpleName}",
                        )
                    }
                    val outcome = RunCloseTargetOutcome(
                        label = label,
                        targetClassName = targetClass,
                        status = "failed",
                        strategy = "AutoCloseable.close",
                        errorClassName = throwable.javaClass.name,
                        message = throwable.message?.take(120),
                    )
                    appendTrace?.let { trace ->
                        path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                    }
                    outcome
                },
            )
    }
    val releaseMethodNames = listOf("close", "release", "destroy", "shutdown", "cancel")
    val selectedMethod = releaseMethodNames.firstNotNullOfOrNull { methodName ->
        target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }?.let { methodName to it }
    }
    if (selectedMethod == null) {
        runCatching {
            appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=none")
        }
        val outcome = RunCloseTargetOutcome(
            label = label,
            targetClassName = targetClass,
            strategy = null,
            status = "skipped",
            errorClassName = null,
            message = null,
        )
        appendTrace?.let { trace ->
            path?.let { emitCloseSummaryTrace(trace, it, outcome) }
        }
        return outcome
    }
    val (strategyName, method) = selectedMethod
    return runCatching { method.invoke(target) }
        .fold(
            onSuccess = {
                runCatching {
                    appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=$strategyName success")
                }
                val outcome = RunCloseTargetOutcome(
                    label = label,
                    targetClassName = targetClass,
                    status = "success",
                    strategy = strategyName,
                    errorClassName = null,
                    message = null,
                )
                appendTrace?.let { trace ->
                    path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                }
                outcome
            },
            onFailure = { throwable ->
                runCatching {
                    appendTrace?.invoke(
                        "UPSTREAM closeQuietly targetClass=$targetClass strategy=$strategyName failed ${throwable.javaClass.simpleName}",
                    )
                }
                val outcome = RunCloseTargetOutcome(
                    label = label,
                    targetClassName = targetClass,
                    status = "failed",
                    strategy = strategyName,
                    errorClassName = throwable.javaClass.name,
                    message = throwable.message?.take(120),
                )
                appendTrace?.let { trace ->
                    path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                }
                outcome
            },
        )
}

internal fun emitCloseSummaryTrace(
    appendTrace: (String) -> Unit,
    path: String,
    outcome: RunCloseTargetOutcome,
) {
    safeAppendTrace(
        appendTrace,
        "UPSTREAM close-summary path=$path label=${outcome.label} status=${outcome.status} strategy=${outcome.strategy ?: "none"} class=${outcome.targetClassName ?: "null"} error=${outcome.errorClassName ?: "none"} message=${outcome.message ?: "none"}",
    )
}
