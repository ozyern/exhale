package com.ozyern.artwork

import org.junit.Assert.*
import org.junit.Test

class ArtworkSourceRegistryTest {
    private val registry = ArtworkSourceRegistry<Any>()

    @Test
    fun memoryCacheImageCanArriveBeforeTheTileIsPlaced() {
        val owner = Any()
        val image = Any()
        registry.updateImage(owner, "cover", image)
        registry.setTitle(owner, "Album")
        registry.register(owner, "album", "cover")
        assertSame(image, registry.select("album")?.image)
        assertEquals("Album", registry.selected?.title)
    }

    @Test
    fun savedTileIdentityMatchesAfterNavigationRecreatesItsComposition() {
        val owner = "stable-owner"
        registry.register(owner, "album", "cover")
        registry.select("album")
        registry.unregister(owner)
        val restored = String(owner.toCharArray())
        registry.register(restored, "album", "cover")
        assertEquals(restored, registry.select("album", returning = true)?.owner)
    }

    @Test
    fun tappedDuplicateOwnsTheTransitionAndItsExactImage() {
        val first = Any()
        val second = Any()
        val image = Any()
        registry.register(first, "album", "first-cover")
        registry.updateImage(first, "first-cover", image)
        registry.register(second, "album", "second-cover")
        registry.touch(first)
        val selected = registry.select("album")!!
        assertSame(first, selected.owner)
        assertSame(image, selected.image)
        assertEquals("first-cover", selected.url)
    }

    @Test
    fun disposingOneDuplicateKeepsTheOtherAndRetainsTheReturnImage() {
        val first = Any()
        val second = Any()
        val image = Any()
        registry.register(first, "album", "cover")
        registry.updateImage(first, "cover", image)
        registry.touch(first)
        registry.select("album")
        registry.register(second, "album", "other-cover")
        registry.unregister(first)
        assertSame(image, registry.select("album", returning = true)?.image)
        assertSame(second, registry.select("album")?.owner)
    }

    @Test
    fun recycledTileCannotAttachAnOldImageToANewItem() {
        val owner = Any()
        registry.register(owner, "old", "old-cover")
        registry.updateImage(owner, "old-cover", Any())
        registry.register(owner, "new", "new-cover")
        registry.updateImage(owner, "old-cover", Any())
        assertNull(registry.select("new")?.image)
        assertNull(registry.select("old"))
    }

    @Test
    fun selectingAnotherItemDoesNotUseThePreviousOwnersTouch() {
        val first = Any()
        val second = Any()
        registry.register(first, "first", null)
        registry.register(second, "second", null)
        registry.touch(first)
        assertSame(second, registry.select("second")?.owner)
        registry.unregister(second)
        assertNull(registry.select("missing"))
    }
}
