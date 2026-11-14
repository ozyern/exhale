package com.ozyern.artwork

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtworkImageStoreTest {
    @Test
    fun bothPanesReuseOneDecodeAndTheExactImage() = runBlocking {
        val store = ArtworkImageStore<Any>()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val image = Any()
        var decodes = 0
        val cover = async {
            store.load("cover") {
                decodes++
                entered.complete(Unit)
                finish.await()
                image
            }
        }
        entered.await()
        val queue = async { store.load("cover") { decodes++; Any() } }
        finish.complete(Unit)
        assertSame(image, cover.await())
        assertSame(image, queue.await())
        assertEquals(1, decodes)
    }

    @Test
    fun failedLoadsCanRetryAndCancelledLoadsReleaseTheLock() = runBlocking {
        val store = ArtworkImageStore<Any>()
        assertNull(store.load("missing") { null })
        val entered = CompletableDeferred<Unit>()
        val loading = launch {
            store.load("cover") {
                entered.complete(Unit)
                CompletableDeferred<Any>().await()
            }
        }
        entered.await()
        loading.cancelAndJoin()
        val recovered = Any()
        assertSame(recovered, store.load("cover") { recovered })
    }

    @Test
    fun cacheStaysBoundedAndRetainsTheMostRecentImages() = runBlocking {
        val store = ArtworkImageStore<Any>(2)
        val first = Any()
        store.load("first") { first }
        val second = Any()
        val third = Any()
        store.load("second") { second }
        store.load("third") { third }
        assertEquals(2, store.images.size)
        assertFalse(store.images.containsKey("first"))
        assertSame(second, store.images["second"])
        assertSame(third, store.images["third"])
    }
}
