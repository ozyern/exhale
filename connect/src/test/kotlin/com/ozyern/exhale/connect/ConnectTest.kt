package com.ozyern.exhale.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConnectProtocolTest {
    @Test fun messagesSurviveTheWire() {
        val track = ConnectTrack("abc123", "Espresso", "Sabrina Carpenter", "Short n' Sweet", "https://x/y.jpg", 175_000)
        val messages = listOf(
            ConnectMessage.Hello("pc", "Desk", "n0nce"),
            ConnectMessage.NowPlaying(track, playing = true, positionMs = 42_000, volume = 70, queue = listOf(track), index = 0, sentAtMs = 9),
            ConnectMessage.Command(ConnectAction.SEEK, 30_000),
            ConnectMessage.Handoff(listOf(track), 0, 12_345),
            ConnectMessage.Rejected(ConnectMessage.Rejected.Reason.WRONG_CODE),
            ConnectMessage.Ping,
        )
        for (message in messages) {
            val line = ConnectCodec.encode(message)
            assertFalse("one message per line", '\n' in line)
            assertEquals(message, ConnectCodec.decode(line))
        }
    }

    @Test fun anUnknownLineIsSkippedNotFatal() {
        assertNull(ConnectCodec.decode("""{"type":"from_the_future","x":1}"""))
        assertNull(ConnectCodec.decode("not json"))
    }
}

class PairingTest {
    @Test fun theSecretOnlyOpensWithTheRightCodeAndNonce() {
        val secret = Pairing.newSecret()
        val sealed = Pairing.seal(secret, "123456", "nonce-a")
        assertEquals(secret, Pairing.unseal(sealed, "123456", "nonce-a"))
        assertNull(Pairing.unseal(sealed, "654321", "nonce-a"))
        assertNull(Pairing.unseal(sealed, "123456", "nonce-b"))
    }

    @Test fun proofsAreBoundToTheirLink() {
        val secret = Pairing.newSecret()
        val proof = Pairing.secretProof(secret, "nonce-a", "phone")
        assertTrue(Pairing.matches(proof, Pairing.secretProof(secret, "nonce-a", "phone")))
        assertFalse(Pairing.matches(proof, Pairing.secretProof(secret, "nonce-b", "phone")))
        assertFalse(Pairing.matches(proof, Pairing.secretProof(Pairing.newSecret(), "nonce-a", "phone")))
        assertFalse(Pairing.matches(Pairing.codeProof("111111", "n", "phone"), Pairing.codeProof("111112", "n", "phone")))
    }

    @Test fun codesAreSixDigits() {
        repeat(200) { assertTrue(Pairing.newCode().matches(Regex("\\d{6}"))) }
    }
}

/** A PC and a phone on this machine: pair once, then the phone gets back in on its own. */
class ConnectLinkTest {
    private fun tempDir(name: String): File = Files.createTempDirectory("exhale-connect-$name").toFile()

    @Test fun pairOnceThenReconnectAndControl() {
        val hostStore = PairStore(tempDir("host"))
        val received = CopyOnWriteArrayList<ConnectMessage>()
        val commandSeen = CountDownLatch(1)
        val host = ConnectHost(
            store = hostStore,
            selfName = { "Test PC" },
            onPeersChanged = {},
            onMessage = { _, message -> received += message; if (message is ConnectMessage.Command) commandSeen.countDown() },
        )
        host.start()
        try {
            val phoneStore = PairStore(tempDir("phone"))
            val connected = CountDownLatch(1)
            val nowPlaying = CountDownLatch(1)
            val client = ConnectClient(
                store = phoneStore,
                selfName = { "Test Phone" },
                broadcastAddress = { InetAddress.getLoopbackAddress() },
                onStatus = { if (it is ConnectStatus.Connected) connected.countDown() },
                onMessage = { if (it is ConnectMessage.NowPlaying) nowPlaying.countDown() },
            )

            val beacon = Beacon(hostStore.selfId, "Test PC").also { it.host = "127.0.0.1" }
            // A wrong code is turned away and pairs nothing.
            host.openPairing()
            assertEquals(ConnectMessage.Rejected.Reason.WRONG_CODE, client.pair(beacon, "000000"))
            assertTrue(phoneStore.devices().isEmpty())

            val code = host.openPairing()
            assertNull(client.pair(beacon, code))
            assertNotNull(phoneStore.device(hostStore.selfId))
            assertEquals(phoneStore.device(hostStore.selfId)!!.secret, hostStore.device(phoneStore.selfId)!!.secret)

            // No code from here on: the client finds its way back with the key alone.
            client.start()
            assertTrue("reconnects by itself", connected.await(10, TimeUnit.SECONDS))

            host.broadcast(ConnectMessage.NowPlaying(playing = true, sentAtMs = 1))
            assertTrue(nowPlaying.await(5, TimeUnit.SECONDS))
            assertTrue(client.send(ConnectMessage.Command(ConnectAction.TOGGLE)))
            assertTrue(commandSeen.await(5, TimeUnit.SECONDS))
            assertTrue(received.any { it is ConnectMessage.Command && it.action == ConnectAction.TOGGLE })
            client.stop()
        } finally {
            host.stop()
        }
    }

    @Test fun aStrangerWithoutTheKeyIsTurnedAway() {
        val hostStore = PairStore(tempDir("host2"))
        val host = ConnectHost(hostStore, { "PC" }, {}, { _, _ -> })
        host.start()
        try {
            val strangerStore = PairStore(tempDir("stranger"))
            // Claims to be paired, with a key the PC never gave out.
            strangerStore.save(PairedDevice(hostStore.selfId, "PC", Pairing.newSecret(), "127.0.0.1"))
            val connected = CountDownLatch(1)
            val stranger = ConnectClient(strangerStore, { "Stranger" }, { InetAddress.getLoopbackAddress() },
                onStatus = { if (it is ConnectStatus.Connected) connected.countDown() }, onMessage = {})
            stranger.start()
            assertFalse(connected.await(4, TimeUnit.SECONDS))
            assertTrue(host.connectedIds().isEmpty())
            stranger.stop()
        } finally {
            host.stop()
        }
    }
}
