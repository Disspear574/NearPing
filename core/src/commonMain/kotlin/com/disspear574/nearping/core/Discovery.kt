package com.disspear574.nearping.core

/**
 * TXT-запись Bonjour. Видна ВСЕМ в сети, не только контактам, поэтому несёт
 * необходимый минимум: версию, публичный ключ и имя. Статус паузы сюда не кладётся —
 * он едет по установленному доверенному соединению.
 */
object TxtRecord {
    const val SERVICE_TYPE = "_nearping._tcp"

    fun of(identity: Identity): Map<String, String> = mapOf(
        "v" to Core.protocolVersion.toString(),
        "pk" to Base64Url.encode(identity.publicKey),
        "n" to identity.displayName,
    )

    /** null — запись чужой версии или испорченная. Такие игнорируются целиком. */
    fun parse(txt: Map<String, String>): Peer? =
        if (version(txt) == Core.protocolVersion) parseAnyVersion(txt) else null

    /** Peer of any protocol version: a colleague on another build is shown, not hidden. */
    fun parseAnyVersion(txt: Map<String, String>): Peer? {
        val key = txt["pk"]?.let { runCatching { Base64Url.decode(it) }.getOrNull() } ?: return null
        if (key.size != IDENTITY_KEY_BYTES) return null
        return Peer(key, txt["n"] ?: "")
    }

    fun version(txt: Map<String, String>): Int? = txt["v"]?.toIntOrNull()

    private const val IDENTITY_KEY_BYTES = 32
}
