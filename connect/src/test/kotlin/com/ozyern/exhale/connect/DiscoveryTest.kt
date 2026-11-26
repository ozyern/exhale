package com.ozyern.exhale.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files

class DiscoveryTest {
    private fun freeTcpPort(): Int = java.net.ServerSocket(0).use { it.localPort }
    private fun freeUdpPort(): Int = DatagramSocket(0).use { it.localPort }
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    /** The PC found by its connection port alone, as a typed address is when UDP never gets through. */
    @Test fun aPcIsFoundByItsHelloWithoutUdp() {
        val store = PairStore(Files.createTempDirectory("exhale-probe").toFile())
        val port = freeTcpPort()
        val host = ConnectHost(store, { "Desk" }, {}, { _, _ -> }, port = port, discoveryPort = freeUdpPort())
        host.start()
        try {
            val found = DiscoveryFinder.probe(listOf(loopback), port = port, timeoutMs = 1_000)
            assertEquals(1, found.size)
            assertEquals("Desk", found.single().name)
            assertEquals(store.selfId, found.single().id)
            assertEquals(loopback.hostAddress, found.single().host)
        } finally {
            host.stop()
        }
    }

    @Test fun nothingListeningFindsNothing() {
        assertTrue(DiscoveryFinder.probe(listOf(loopback), port = freeTcpPort(), timeoutMs = 300).isEmpty())
    }

    /** Busy when it started, the responder keeps trying and answers once the port is free. */
    @Test fun theResponderBindsOnceThePortFreesUp() {
        val port = freeUdpPort()
        val squatter = DatagramSocket(null).apply { bind(InetSocketAddress(port)) }
        val responder = DiscoveryResponder(port) { Beacon("pc", "Desk") }
        responder.start()
        try {
            Thread.sleep(300)
            squatter.close()
            val deadline = System.currentTimeMillis() + 8_000
            var answered = false
            DatagramSocket().use { asker ->
                asker.soTimeout = 300
                val question = "EXHALE_CONNECT?".toByteArray()
                while (!answered && System.currentTimeMillis() < deadline) {
                    asker.send(DatagramPacket(question, question.size, loopback, port))
                    answered = runCatching { asker.receive(DatagramPacket(ByteArray(512), 512)) }.isSuccess
                }
            }
            assertTrue("the responder never came back", answered)
        } finally {
            responder.stop()
        }
    }
}
