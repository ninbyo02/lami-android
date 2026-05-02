package io.github.ninbyo02.lami.ui.screens.home

import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.util.Locale

internal object AcceleratorProbe {
    private const val LOG_TAG = "AcceleratorProbe"

    @Volatile
    private var hasLogged = false

    @Volatile
    private var cachedSnapshot: AcceleratorProbeSnapshot? = null

    fun captureSnapshot(forceRefresh: Boolean = false): AcceleratorProbeSnapshot {
        if (!forceRefresh) {
            cachedSnapshot?.let { return it }
        }

        val snapshot = captureSnapshotUncached()
        cachedSnapshot = snapshot
        maybeLogOnce(snapshot)
        return snapshot
    }

    private fun captureSnapshotUncached(): AcceleratorProbeSnapshot {
        var probeError: String? = null
        val nnapiDevices = runCatching {
            fetchNnapiDeviceNamesSafely()
        }.getOrElse {
            probeError = it.javaClass.simpleName
            emptyList()
        }

        val gpuProbeResult = probeGpuInfoSafely()

        return AcceleratorProbeSnapshot(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceBoard = Build.BOARD,
            androidSdk = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            cpuCoreCount = runCatching { Runtime.getRuntime().availableProcessors() }.getOrNull(),
            cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull(),
            gpuVendor = gpuProbeResult.vendor,
            gpuRenderer = gpuProbeResult.renderer,
            gpuVersion = gpuProbeResult.version,
            nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            nnapiDeprecatedWarning = Build.VERSION.SDK_INT >= 35,
            nnapiDevices = nnapiDevices,
            probeSource = "Build+Runtime+NNAPIReflectionSafeStub",
            probeError = probeError,
            gpuProbeSource = gpuProbeResult.source,
            gpuProbeError = gpuProbeResult.error,
        )
    }

    private fun maybeLogOnce(snapshot: AcceleratorProbeSnapshot) {
        if (!BuildConfig.DEBUG || hasLogged) return
        synchronized(this) {
            if (hasLogged) return
            val message = String.format(
                Locale.US,
                "sdk=%d abi=%s cpuCores=%s gpuRenderer=%s nnapiAvailable=%s nnapiDevices=%d",
                snapshot.androidSdk,
                snapshot.cpuAbi ?: "unknown",
                snapshot.cpuCoreCount?.toString() ?: "unknown",
                snapshot.gpuRenderer ?: "unknown",
                snapshot.nnapiAvailable,
                snapshot.nnapiDevices.size,
            )
            Log.d(LOG_TAG, message)
            hasLogged = true
        }
    }

    private fun fetchNnapiDeviceNamesSafely(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return runCatching {
            val nnApiImplClass = Class.forName("android.media.MediaCodecInfo")
            nnApiImplClass
            emptyList<String>()
        }.getOrDefault(emptyList())
    }

    private fun probeGpuInfoSafely(): GpuProbeResult {
        return runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                return GpuProbeResult(source = "egl-pbuffer", error = "eglGetDisplay failed")
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                EGL14.eglTerminate(display)
                return GpuProbeResult(source = "egl-pbuffer", error = "eglInitialize failed")
            }

            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE
            try {
                val configAttribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglChooseConfig failed")
                }
                val config = configs[0] ?: return GpuProbeResult(source = "egl-pbuffer", error = "eglConfig missing")

                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                if (context == EGL14.EGL_NO_CONTEXT) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglCreateContext failed")
                }

                val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
                if (surface == EGL14.EGL_NO_SURFACE) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglCreatePbufferSurface failed")
                }

                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglMakeCurrent failed")
                }

                GpuProbeResult(
                    vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                    renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                    version = GLES20.glGetString(GLES20.GL_VERSION),
                    source = "egl-pbuffer",
                )
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }.getOrElse {
            GpuProbeResult(source = "egl-pbuffer", error = it.javaClass.simpleName)
        }
    }

    private data class GpuProbeResult(
        val vendor: String? = null,
        val renderer: String? = null,
        val version: String? = null,
        val source: String? = null,
        val error: String? = null,
    )
}
