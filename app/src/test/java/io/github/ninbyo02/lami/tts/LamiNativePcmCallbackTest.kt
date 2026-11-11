package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class LamiNativePcmCallbackTest {
    @Test fun `exposes the concrete boxed-return method required by sherpa JNI`() {
        val method = LamiNativePcmCallback::class.java.getMethod("invoke", FloatArray::class.java)
        assertEquals(java.lang.Integer::class.java, method.returnType)
    }

    @Test fun `forwards pcm and returns the continue marker`() {
        var received: FloatArray? = null
        val callback = LamiNativePcmCallback { received = it }
        val samples = floatArrayOf(.1f, -.2f)
        assertEquals(1, callback.invoke(samples))
        assertSame(samples, received)
    }
}
