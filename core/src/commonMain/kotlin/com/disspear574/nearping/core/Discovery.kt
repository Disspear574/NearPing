package com.disspear574.nearping.core

/** Bonjour TXT record; visible to everyone on the network, so it carries the bare minimum. */
object TxtRecord {
    const val SERVICE_TYPE = "_nearping._tcp"

    fun of(identity: Identity): Map<String, String> = mapOf(
        "v" to Core.protocolVersion.toString(),
        "pk" to Base64Url.encode(identity.publicKey),
        "n" to identity.displayName,
    )

    fun parse(txt: Map<String, String>): Peer? =
        if (version(txt) == Core.protocolVersion) parseAnyVersion(txt) else null

    fun parseAnyVersion(txt: Map<String, String>): Peer? {
        val key = txt["pk"]?.let { runCatching { Base64Url.decode(it) }.getOrNull() } ?: return null
        if (key.size != IDENTITY_KEY_BYTES) return null
        return Peer(key, txt["n"] ?: "")
    }

    fun version(txt: Map<String, String>): Int? = txt["v"]?.toIntOrNull()

    private const val IDENTITY_KEY_BYTES = 32
}
