package io.github.ninbyo02.lami.local

import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.util.Locale

data class QnnDelegateProbeResult(
    val isSm8750Likely: Boolean,
    val socHints: List<String>,
    val qnnClassFound: Boolean,
    val qnnDelegateCreated: Boolean,
    val htpBackendRequested: Boolean,
    val nativeLibraryDir: String?,
    val errorClass: String?,
    val errorMessage: String?,
)

internal data class QnnDeviceBuildInfo(
    val hardware: String?,
    val board: String?,
    val device: String?,
    val product: String?,
    val model: String?,
    val socModel: String?,
    val socManufacturer: String?,
)

object QnnDelegateProbe {
    private const val LOG_TAG = "QnnDelegateProbe"
    private const val MAX_ERROR_TEXT = 220

    private val qnnDelegateClassCandidates = listOf(
        "com.qualcomm.qti.qnn.QnnDelegate",
        "com.qualcomm.qti.qnn.delegate.QnnDelegate",
        "com.qualcomm.qti.QnnDelegate",
        "org.tensorflow.lite.qnn.QnnDelegate",
        "org.tensorflow.lite.task.qnn.QnnDelegate",
    )

    fun probe(context: Context): QnnDelegateProbeResult {
        val appContext = context.applicationContext ?: context
        val nativeLibraryDir = appContext.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
        val socHints = collectSocHints(readBuildInfo())
        val isSm8750Likely = isSm8750LikelyFromHints(socHints)
        val delegateClassResult = findFirstClass(qnnDelegateClassCandidates)

        val result = when {
            delegateClassResult.clazz == null -> {
                QnnDelegateProbeResult(
                    isSm8750Likely = isSm8750Likely,
                    socHints = socHints,
                    qnnClassFound = false,
                    qnnDelegateCreated = false,
                    htpBackendRequested = false,
                    nativeLibraryDir = nativeLibraryDir,
                    errorClass = delegateClassResult.errorClass ?: "ClassNotFoundException",
                    errorMessage = delegateClassResult.errorMessage
                        ?: "No QNN delegate class found",
                )
            }
            !isSm8750Likely -> {
                QnnDelegateProbeResult(
                    isSm8750Likely = false,
                    socHints = socHints,
                    qnnClassFound = true,
                    qnnDelegateCreated = false,
                    htpBackendRequested = false,
                    nativeLibraryDir = nativeLibraryDir,
                    errorClass = "SkippedNonSm8750",
                    errorMessage = "QNN delegate creation skipped because SM8750 was not detected",
                )
            }
            else -> {
                createDelegateWithHtpBackend(
                    delegateClass = delegateClassResult.clazz,
                    nativeLibraryDir = nativeLibraryDir,
                    socHints = socHints,
                    isSm8750Likely = true,
                )
            }
        }

        logResult(result)
        return result
    }

    internal fun collectSocHints(buildInfo: QnnDeviceBuildInfo): List<String> {
        return listOf(
            "HARDWARE" to buildInfo.hardware,
            "BOARD" to buildInfo.board,
            "DEVICE" to buildInfo.device,
            "PRODUCT" to buildInfo.product,
            "MODEL" to buildInfo.model,
            "SOC_MODEL" to buildInfo.socModel,
            "SOC_MANUFACTURER" to buildInfo.socManufacturer,
        ).mapNotNull { (label, value) ->
            value?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { "$label=$it" }
        }
    }

    internal fun isSm8750LikelyFromHints(socHints: List<String>): Boolean {
        return socHints.any { hint ->
            val normalized = hint.lowercase(Locale.US)
            normalized.contains("sm8750") ||
                normalized.contains("snapdragon 8 elite")
        }
    }

    private fun readBuildInfo(): QnnDeviceBuildInfo {
        return QnnDeviceBuildInfo(
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            model = Build.MODEL,
            socModel = readBuildStaticString("SOC_MODEL"),
            socManufacturer = readBuildStaticString("SOC_MANUFACTURER"),
        )
    }

    private fun readBuildStaticString(fieldName: String): String? {
        return runCatching {
            Build::class.java.getField(fieldName).get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun createDelegateWithHtpBackend(
        delegateClass: Class<*>,
        nativeLibraryDir: String?,
        socHints: List<String>,
        isSm8750Likely: Boolean,
    ): QnnDelegateProbeResult {
        var htpBackendRequested = false
        return runCatching {
            val optionsClass = findOptionsClass(delegateClass)
                ?: throw ClassNotFoundException("${delegateClass.name}.Options")
            val options = newInstance(optionsClass)
            htpBackendRequested = configureHtpBackend(options)
            configureSkelLibraryDir(options, nativeLibraryDir)
            val delegate = createDelegate(delegateClass, options)
            closeDelegate(delegate)
            QnnDelegateProbeResult(
                isSm8750Likely = isSm8750Likely,
                socHints = socHints,
                qnnClassFound = true,
                qnnDelegateCreated = true,
                htpBackendRequested = htpBackendRequested,
                nativeLibraryDir = nativeLibraryDir,
                errorClass = null,
                errorMessage = null,
            )
        }.getOrElse { throwable ->
            val root = rootCause(throwable)
            QnnDelegateProbeResult(
                isSm8750Likely = isSm8750Likely,
                socHints = socHints,
                qnnClassFound = true,
                qnnDelegateCreated = false,
                htpBackendRequested = htpBackendRequested,
                nativeLibraryDir = nativeLibraryDir,
                errorClass = root.javaClass.simpleName,
                errorMessage = sanitizeErrorMessage(root.message),
            )
        }
    }

    private fun findOptionsClass(delegateClass: Class<*>): Class<*>? {
        val nestedOptions = (delegateClass.classes.asList() + delegateClass.declaredClasses.asList())
            .firstOrNull { it.simpleName == "Options" }
        if (nestedOptions != null) return nestedOptions
        return runCatching { Class.forName("${delegateClass.name}\$Options") }.getOrNull()
    }

    private fun newInstance(clazz: Class<*>): Any {
        val constructor = clazz.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: clazz.constructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: throw NoSuchMethodException("${clazz.name}()")
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun configureHtpBackend(options: Any): Boolean {
        val optionsClass = options.javaClass
        val methods = (optionsClass.methods.asList() + optionsClass.declaredMethods.asList())
            .filter { method ->
                method.name == "setBackendType" ||
                    method.name == "setBackend" ||
                    method.name.contains("backendType", ignoreCase = true)
            }
            .filter { it.parameterTypes.size == 1 }

        methods.forEach { method ->
            val argument = resolveHtpBackendArgument(optionsClass, method.parameterTypes.first())
                ?: return@forEach
            method.isAccessible = true
            method.invoke(options, argument)
            return true
        }
        throw NoSuchMethodException("No HTP backend setter found on ${optionsClass.name}")
    }

    private fun resolveHtpBackendArgument(optionsClass: Class<*>, parameterType: Class<*>): Any? {
        if (parameterType == String::class.java) return "HTP_BACKEND"
        if (parameterType == Int::class.javaPrimitiveType || parameterType == Int::class.javaObjectType) {
            return findStaticNumberValue(optionsClass, "HTP")?.toInt()
        }
        if (parameterType == Long::class.javaPrimitiveType || parameterType == Long::class.javaObjectType) {
            return findStaticNumberValue(optionsClass, "HTP")?.toLong()
        }
        if (parameterType.isEnum) {
            return parameterType.enumConstants
                ?.firstOrNull { enumValue ->
                    enumValue.toString().contains("HTP", ignoreCase = true)
                }
        }
        return findStaticFieldValue(parameterType, "HTP")
            ?: findStaticFieldValue(optionsClass, "HTP")
    }

    private fun findStaticNumberValue(clazz: Class<*>, keyword: String): Number? {
        return (clazz.fields.asList() + clazz.declaredFields.asList())
            .firstNotNullOfOrNull { field ->
                if (!field.name.contains(keyword, ignoreCase = true)) return@firstNotNullOfOrNull null
                runCatching {
                    field.isAccessible = true
                    field.get(null) as? Number
                }.getOrNull()
            }
    }

    private fun findStaticFieldValue(clazz: Class<*>, keyword: String): Any? {
        return (clazz.fields.asList() + clazz.declaredFields.asList())
            .firstNotNullOfOrNull { field ->
                if (!field.name.contains(keyword, ignoreCase = true)) return@firstNotNullOfOrNull null
                runCatching {
                    field.isAccessible = true
                    field.get(null)
                }.getOrNull()
            }
    }

    private fun configureSkelLibraryDir(options: Any, nativeLibraryDir: String?) {
        if (nativeLibraryDir.isNullOrBlank()) return
        val optionsClass = options.javaClass
        val methods = (optionsClass.methods.asList() + optionsClass.declaredMethods.asList())
            .filter { method ->
                method.name == "setSkelLibraryDir" ||
                    method.name == "setSkeletonLibraryDir" ||
                    method.name.contains("skelLibraryDir", ignoreCase = true) ||
                    method.name.contains("nativeLibraryDir", ignoreCase = true)
            }
            .filter { it.parameterTypes.size == 1 }

        methods.firstOrNull { method ->
            val parameterType = method.parameterTypes.first()
            parameterType == String::class.java || parameterType.name == "java.io.File"
        }?.let { method ->
            val argument: Any = if (method.parameterTypes.first() == String::class.java) {
                nativeLibraryDir
            } else {
                java.io.File(nativeLibraryDir)
            }
            method.isAccessible = true
            method.invoke(options, argument)
        }
    }

    private fun createDelegate(delegateClass: Class<*>, options: Any): Any {
        val optionsClass = options.javaClass
        val optionsConstructor = (delegateClass.declaredConstructors.asList() + delegateClass.constructors.asList())
            .firstOrNull { constructor ->
                constructor.parameterTypes.size == 1 &&
                    constructor.parameterTypes.first().isAssignableFrom(optionsClass)
            }
        if (optionsConstructor != null) {
            optionsConstructor.isAccessible = true
            return optionsConstructor.newInstance(options)
        }

        val noArgConstructor = (delegateClass.declaredConstructors.asList() + delegateClass.constructors.asList())
            .firstOrNull { it.parameterTypes.isEmpty() }
            ?: throw NoSuchMethodException("${delegateClass.name}(Options)")
        noArgConstructor.isAccessible = true
        return noArgConstructor.newInstance()
    }

    private fun closeDelegate(delegate: Any) {
        when (delegate) {
            is AutoCloseable -> delegate.close()
            else -> {
                val closeMethod = (delegate.javaClass.methods.asList() + delegate.javaClass.declaredMethods.asList())
                    .firstOrNull { it.name == "close" && it.parameterTypes.isEmpty() }
                closeMethod?.let {
                    it.isAccessible = true
                    it.invoke(delegate)
                }
            }
        }
    }

    private fun findFirstClass(classNames: List<String>): ClassProbeResult {
        var lastErrorClass: String? = null
        var lastErrorMessage: String? = null
        classNames.forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrElse { throwable ->
                val root = rootCause(throwable)
                lastErrorClass = root.javaClass.simpleName
                lastErrorMessage = sanitizeErrorMessage(root.message ?: className)
                return@forEach
            }
            return ClassProbeResult(clazz = clazz)
        }
        return ClassProbeResult(
            errorClass = lastErrorClass,
            errorMessage = lastErrorMessage,
        )
    }

    private fun rootCause(throwable: Throwable): Throwable {
        return when (throwable) {
            is InvocationTargetException -> throwable.targetException?.let(::rootCause) ?: throwable
            else -> throwable.cause?.takeIf { it !== throwable }?.let(::rootCause) ?: throwable
        }
    }

    private fun sanitizeErrorMessage(message: String?): String? {
        return message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_ERROR_TEXT)
    }

    private fun logResult(result: QnnDelegateProbeResult) {
        Log.d(LOG_TAG, "QNN probe: socHints=${result.socHints.joinToString(";").take(360)}")
        Log.d(LOG_TAG, "QNN probe: classFound=${result.qnnClassFound}")
        Log.d(LOG_TAG, "QNN probe: delegateCreated=${result.qnnDelegateCreated}")
        Log.d(
            LOG_TAG,
            "QNN probe: error=${listOfNotNull(result.errorClass, result.errorMessage).joinToString(":").ifBlank { "none" }}",
        )
    }

    private data class ClassProbeResult(
        val clazz: Class<*>? = null,
        val errorClass: String? = null,
        val errorMessage: String? = null,
    )
}
