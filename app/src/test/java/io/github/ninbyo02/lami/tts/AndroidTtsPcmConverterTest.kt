package io.github.ninbyo02.lami.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AndroidTtsPcmConverterTest {
    @Test fun `partial stereo frames carry across arbitrarily split callback bytes`() {
        val converter = AndroidTtsPcmConverter(AndroidTtsPcmFormat(48_000, 2, 2), true)
        val bytes = pcm(shortArrayOf(-32768, 32767, 1000, -1000))
        val output = mutableListOf<Short>()
        for (byte in bytes) output += converter.convert(byteArrayOf(byte)).toList()
        output += converter.finish().toList()
        assertArrayEquals(shortArrayOf(-32768, 32767, 1000, -1000), output.toShortArray())
    }

    @Test fun `USB resampling keeps interpolation phase identical across chunks`() {
        for (rate in listOf(8_000, 16_000, 24_000, 44_100, 48_000)) for (channels in 1..2) {
            val samples = ShortArray(1003 * channels) { ((it * 47) % 30000 - 15000).toShort() }
            val bytes = pcm(samples)
            val whole = AndroidTtsPcmConverter(AndroidTtsPcmFormat(rate, 2, channels), true)
            val expected = whole.convert(bytes) + whole.finish()
            val chunked = AndroidTtsPcmConverter(AndroidTtsPcmFormat(rate, 2, channels), true)
            val result = mutableListOf<Short>()
            var offset = 0
            while (offset < bytes.size) {
                val end = (offset + 7).coerceAtMost(bytes.size)
                result += chunked.convert(bytes.copyOfRange(offset, end)).toList()
                offset = end
            }
            result += chunked.finish().toList()
            assertArrayEquals("rate=$rate channels=$channels", expected, result.toShortArray())
            assertEquals((1003L * 48_000 / rate * 2).toInt(), expected.size)
        }
    }

    @Test fun `USB mono24k interpolation and channel duplication preserves endpoints`() {
        val converter = AndroidTtsPcmConverter(AndroidTtsPcmFormat(24_000, 2, 1), true)
        assertArrayEquals(shortArrayOf(0, 0, 500, 500, 1000, 1000, 1000, 1000),
            converter.convert(pcm(shortArrayOf(0, 1000))) + converter.finish())
    }

    @Test fun `standard output preserves native rate channels and PCM16 samples`() {
        val samples = shortArrayOf(-32768, -1, 0, 32767)
        val converter = AndroidTtsPcmConverter(AndroidTtsPcmFormat(22_050, 2, 2), false)
        assertEquals(AndroidTtsPcmFormat(22_050, 2, 2), converter.outputFormat)
        assertArrayEquals(samples, converter.convert(pcm(samples)))
    }

    @Test fun `unsigned8bit and float formats convert with bounded amplitude`() {
        val eight = AndroidTtsPcmConverter(AndroidTtsPcmFormat(16_000, 3, 1), false)
        assertArrayEquals(shortArrayOf(-32768, 0, 32512), eight.convert(byteArrayOf(0, -128, -1)))
        val floats = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putFloat(-1f).putFloat(0f).putFloat(1f).array()
        val floating = AndroidTtsPcmConverter(AndroidTtsPcmFormat(24_000, 4, 1), false)
        assertArrayEquals(shortArrayOf(-32767, 0, 32767), floating.convert(floats))
        val invalid = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN).array()
        assertThrows(IllegalArgumentException::class.java) { floating.convert(invalid) }
    }

    @Test fun `unsupported formats rates and channels fail before output creation`() {
        assertThrows(IllegalArgumentException::class.java) { AndroidTtsPcmFormat(24_000, 99, 1) }
        assertThrows(IllegalArgumentException::class.java) { AndroidTtsPcmFormat(192_000, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { AndroidTtsPcmFormat(24_000, 2, 3) }
    }

    private fun pcm(samples: ShortArray): ByteArray = ByteBuffer.allocate(samples.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(samples) }.array()
}
