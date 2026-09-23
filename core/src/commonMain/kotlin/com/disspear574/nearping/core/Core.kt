package com.disspear574.nearping.core

object Core {
    const val protocolVersion: Int = 1

    /** Короткий отпечаток ключа для глаз человека: первые 4 байта, две группы по 4 hex. */
    fun fingerprint(bytes: ByteArray): String {
        require(bytes.size >= 4) { "need at least 4 bytes, got ${bytes.size}" }
        val hex = buildString {
            for (i in 0 until 4) {
                val v = bytes[i].toInt() and 0xFF
                append(v.toString(16).padStart(2, '0'))
            }
        }
        return "${hex.substring(0, 4)}-${hex.substring(4, 8)}"
    }
}
