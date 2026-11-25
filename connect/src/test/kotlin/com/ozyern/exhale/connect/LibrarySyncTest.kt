package com.ozyern.exhale.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LibrarySyncTest {
    private fun sync(name: String) = LibrarySync(Files.createTempDirectory("exhale-sync-$name").toFile())
    private fun track(id: String) = ConnectTrack(id, "Song $id", "Artist")

    /** Both libraries apply what the other sends, as the apps would, until neither has news. */
    private fun exchange(a: LibrarySync, aLikes: MutableSet<String>, b: LibrarySync, bLikes: MutableSet<String>) {
        fun apply(merge: LibrarySync.Merge, likes: MutableSet<String>) {
            likes += merge.like.map { it.id }
            likes -= merge.unlike.toSet()
        }
        apply(b.merge(a.snapshot()), bLikes)
        apply(a.merge(b.snapshot()), aLikes)
    }

    @Test fun pairingCombinesLikesAndNeverDeletes() {
        val phone = sync("phone"); val pc = sync("pc")
        val phoneLikes = mutableSetOf("a", "b"); val pcLikes = mutableSetOf("b", "c")
        phone.localLikes(phoneLikes.map(::track)); pc.localLikes(pcLikes.map(::track))
        exchange(phone, phoneLikes, pc, pcLikes)
        assertEquals(setOf("a", "b", "c"), phoneLikes)
        assertEquals(setOf("a", "b", "c"), pcLikes)
    }

    @Test fun anUnlikeOnOneSideRemovesItOnTheOther() {
        val phone = sync("phone2"); val pc = sync("pc2")
        val phoneLikes = mutableSetOf("a", "b"); val pcLikes = mutableSetOf("a", "b")
        phone.localLikes(phoneLikes.map(::track)); pc.localLikes(pcLikes.map(::track))
        exchange(phone, phoneLikes, pc, pcLikes)

        phoneLikes -= "a"
        val delta = phone.localLikes(phoneLikes.map(::track), nowMs = 1_000)
        assertEquals(listOf("a" to false), delta.map { it.track.id to it.liked })
        val merge = pc.merge(ConnectMessage.Library(likes = delta))
        assertEquals(listOf("a"), merge.unlike)
        pcLikes -= merge.unlike.toSet()
        // Applying it does not echo back as a change of the PC's own.
        assertTrue(pc.localLikes(pcLikes.map(::track), nowMs = 2_000).isEmpty())
    }

    @Test fun theNewestChangeWins() {
        val phone = sync("phone3"); val pc = sync("pc3")
        phone.localLikes(listOf(track("a"))); pc.localLikes(listOf(track("a")))
        // Unliked on the phone at 5s, liked again on the PC at 9s: liked wins.
        val unlike = phone.localLikes(emptyList(), nowMs = 5_000)
        val pcMerge = pc.merge(ConnectMessage.Library(likes = unlike))
        assertEquals(listOf("a"), pcMerge.unlike)
        val relike = pc.localLikes(listOf(track("a")), nowMs = 9_000)
        val phoneMerge = phone.merge(ConnectMessage.Library(likes = relike))
        assertEquals(listOf("a"), phoneMerge.like.map { it.id })
        // An older unlike arriving late changes nothing.
        assertTrue(phone.merge(ConnectMessage.Library(likes = unlike)).isEmpty)
    }

    @Test fun playsAreTakenOnceEach() {
        val phone = sync("phone4"); val pc = sync("pc4")
        val played = PlayEntry(track("a"), atMs = 100_000)
        assertEquals(1, phone.localPlays(listOf(played)).size)
        assertTrue("told once", phone.localPlays(listOf(played)).isEmpty())
        assertEquals(listOf(played), pc.merge(ConnectMessage.Library(plays = listOf(played))).plays)
        // The PC inserting it into its history and reporting it back is the same play, not a new one.
        assertTrue(pc.localPlays(listOf(PlayEntry(track("a"), atMs = 100_500))).isEmpty())
    }
}
