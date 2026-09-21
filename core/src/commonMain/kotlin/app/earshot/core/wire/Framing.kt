package app.earshot.core.wire

/**
 * Поток TCP не знает границ сообщений, поэтому каждое предваряется длиной:
 * четыре байта big-endian, затем ровно столько байт тела.
 */
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

    /**
     * Собирает кадры из произвольно нарезанных кусков. Один вызов [feed] может
     * вернуть ноль, один или несколько сообщений — как лягут границы пакетов.
     */
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
                // Длина приходит из сети: без проверки чужой пакет заставит нас
                // копить гигабайты в ожидании кадра, которого не будет.
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
