package com.disspear574.earshot.core

/** base64url без выравнивания. Свой, чтобы не тащить зависимость ради двадцати строк. */
object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            sb.append(ALPHABET[b0 ushr 2])
            sb.append(ALPHABET[((b0 and 0x03) shl 4) or if (b1 < 0) 0 else (b1 ushr 4)])
            if (b1 >= 0) sb.append(ALPHABET[((b1 and 0x0F) shl 2) or if (b2 < 0) 0 else (b2 ushr 6)])
            if (b2 >= 0) sb.append(ALPHABET[b2 and 0x3F])
            i += 3
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length * 3 / 4 + 1)
        var acc = 0
        var bits = 0
        for (ch in text) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) continue
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((acc ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
