package app.earshot.core.wire

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WireTest {
    @Test
    fun pingRoundTrips() {
        val original = Message.Ping(id = "p1", urgent = true)
        assertEquals(original, Wire.decode(Wire.encode(original)))
    }

    @Test
    fun ackRoundTrips() {
        val original = Message.Ack(id = "p1", state = AckState.SEEN)
        assertEquals(original, Wire.decode(Wire.encode(original)))
    }

    @Test
    fun presenceCarriesNullWhenNotPaused() {
        val original = Message.Presence(pausedUntilEpochSeconds = null)
        assertEquals(original, Wire.decode(Wire.encode(original)))
    }

    @Test
    fun encodedMessageCarriesProtocolVersion() {
        val json = Wire.encode(Message.Ping("p1", false)).decodeToString()
        assertTrue(json.contains("\"v\":1"), "нет версии протокола: $json")
    }

    @Test
    fun foreignProtocolVersionIsRejected() {
        val alien = """{"v":99,"body":{"type":"ping","id":"p1","urgent":false}}""".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { Wire.decode(alien) }
    }

    @Test
    fun unknownFieldsAreTolerated() {
        val future = """{"v":1,"body":{"type":"ping","id":"p1","urgent":true,"mood":"loud"}}""".encodeToByteArray()
        assertEquals(Message.Ping("p1", true), Wire.decode(future))
    }
}

class FramingTest {
    @Test
    fun framePrefixesFourByteLength() {
        val framed = Framing.frame(byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(0, 0, 0, 3, 1, 2, 3), framed)
    }

    @Test
    fun parserSplitsTwoMessagesFromOneChunk() {
        val chunk = Framing.frame("one".encodeToByteArray()) + Framing.frame("two".encodeToByteArray())
        val out = Framing.Parser().feed(chunk)
        assertEquals(listOf("one", "two"), out.map { it.decodeToString() })
    }

    @Test
    fun parserWaitsForSplitFrame() {
        val framed = Framing.frame("hello".encodeToByteArray())
        val parser = Framing.Parser()
        assertEquals(emptyList(), parser.feed(framed.copyOfRange(0, 3)))
        assertEquals(listOf("hello"), parser.feed(framed.copyOfRange(3, framed.size)).map { it.decodeToString() })
    }

    @Test
    fun parserHandlesByteAtATime() {
        val framed = Framing.frame("drip".encodeToByteArray())
        val parser = Framing.Parser()
        val collected = framed.flatMap { parser.feed(byteArrayOf(it)) }
        assertEquals(listOf("drip"), collected.map { it.decodeToString() })
    }

    @Test
    fun oversizedFrameIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            Framing.frame(ByteArray(Framing.MAX_FRAME + 1))
        }
    }

    @Test
    fun absurdDeclaredLengthIsRefused() {
        val evil = byteArrayOf(0x7F, -1, -1, -1)
        assertFailsWith<IllegalArgumentException> { Framing.Parser().feed(evil) }
    }
}
