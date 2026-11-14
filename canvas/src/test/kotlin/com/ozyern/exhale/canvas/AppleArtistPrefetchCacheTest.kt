package com.ozyern.exhale.canvas

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AppleArtistPrefetchCacheTest {
    @Test fun concurrentPrefetchAndPageLookupShareOneLoadAndReuseTheId() = runBlocking {
        val key = "artist-id|test|${UUID.randomUUID()}"
        val loads = AtomicInteger()
        val answers = (1..20).map {
            async { AppleMusicCatalog.cached(key) { loads.incrementAndGet(); delay(30); "479756766" } }
        }.awaitAll()
        assertTrue(answers.all { it == "479756766" })
        assertEquals(1, loads.get())
        assertEquals("479756766", AppleMusicCatalog.cached(key) { error("Cached ID should skip another search") })
    }
}
