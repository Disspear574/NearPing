package com.disspear574.nearping.core.wire

/** Length-prefixed framing: 4 bytes big-endian, then the body. */
object Framing {
    const val MAX_FRAME: Int = 64 * 1024

    fun frame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME) { "frame too large: ${payload.size}" }
        val n = payload.size
        val header = byteArrayOf(
            (n ushr 24).toByte(),
            (n ushr 16).toByte(),
            (n ushr 8).toByte(),
            n.toByte(),
        )
        return header + payload
    }

    /** Reassembles frames from arbitrarily split chunks. */
    class Parser {
        private var buffer = ByteArray(0)

        fun feed(chunk: ByteArray): List<ByteArray> {
            buffer += chunk
            val out = mutableListOf<ByteArray>()
            while (buffer.size >= HEADER) {
                val declared = ((buffer[0].toInt() and 0xFF) shl 24) or
                    ((buffer[1].toInt() and 0xFF) shl 16) or
                    ((buffer[2].toInt() and 0xFF) shl 8) or
                    (buffer[3].toInt() and 0xFF)
                // The length comes from the network; unchecked it would let a peer make us buffer gigabytes.
                require(declared in 0..MAX_FRAME) { "bad frame length: $declared" }
                if (buffer.size < HEADER + declared) break
                out += buffer.copyOfRange(HEADER, HEADER + declared)
                buffer = buffer.copyOfRange(HEADER + declared, buffer.size)
            }
            return out
        }

        private companion object {
            const val HEADER = 4
        }
    }
}
