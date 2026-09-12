package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class IdleCloseableResourceTest {
    private class Resource : AutoCloseable {
        var closes = 0
        override fun close() { closes++ }
    }
    private val timers = mutableListOf<() -> Unit>()
    private fun cache() = IdleCloseableResource<String, Resource> { callback ->
        timers += callback
        AutoCloseable { }
    }

    @Test fun reusesResourceAndIgnoresStaleExpiry() {
        val cache = cache()
        val resource = Resource()
        assertEquals(false, cache.use("model", { resource }) { _, reused -> reused })
        val oldExpiry = timers.last()
        assertEquals(true, cache.use("model", { error("must reuse") }) { value, reused ->
            assertSame(resource, value)
            reused
        })
        oldExpiry()
        assertEquals(0, resource.closes)
        timers.last()()
        assertEquals(1, resource.closes)
        cache.close()
        assertEquals(1, resource.closes)
    }

    @Test fun modelChangeClosesPreviousBeforeCreatingNext() {
        val cache = cache()
        val first = Resource()
        cache.use("path-size-mtime-a", { first }) { _, _ -> Unit }
        val second = Resource()
        cache.use("path-size-mtime-b", {
            assertEquals(1, first.closes)
            second
        }) { _, reused -> assertFalse(reused) }
        cache.close()
        assertEquals(1, second.closes)
    }

    @Test fun failureDisposesAndNextCallRetries() {
        val cache = cache()
        val first = Resource()
        assertThrows(IllegalStateException::class.java) {
            cache.use("model", { first }) { _, _ -> error("native failure") }
        }
        assertEquals(1, first.closes)
        assertNull(cache.use("model", { null }) { _, _ -> 1 })
        val next = Resource()
        assertEquals(2, cache.use("model", { next }) { _, reused -> assertFalse(reused); 2 })
        cache.close()
    }

    @Test fun closeWaitsUntilActiveNativeCallFinishes() {
        val cache = cache()
        val resource = Resource()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val worker = thread {
            cache.use("model", { resource }) { _, _ ->
                entered.countDown()
                assertTrue(finish.await(5, TimeUnit.SECONDS))
                assertEquals(0, resource.closes)
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val closer = thread { closeStarted.countDown(); cache.close(); closed.countDown() }
        assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
        assertFalse(closed.await(50, TimeUnit.MILLISECONDS))
        finish.countDown()
        worker.join(5000)
        closer.join(5000)
        assertFalse(worker.isAlive)
        assertFalse(closer.isAlive)
        assertEquals(1, resource.closes)
    }
}
