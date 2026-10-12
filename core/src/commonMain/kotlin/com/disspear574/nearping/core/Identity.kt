package com.disspear574.nearping.core

/** Device identity: the private key stays in the platform store, the name is user-chosen. */
class Identity(
    val publicKey: ByteArray,
    displayName: String,
) {
    var displayName: String = normalize(displayName)
        set(value) { field = normalize(value) }

    val fingerprint: String get() = Core.fingerprint(publicKey)

    private fun normalize(name: String): String = name.trim().ifBlank { FALLBACK_NAME }

    companion object {
        const val FALLBACK_NAME = "NearPing"
    }
}

/** Another machine, recognised by key rather than name or address. */
class Peer(
    val publicKey: ByteArray,
    displayName: String,
) {
    val displayName: String = displayName.trim().ifBlank { Identity.FALLBACK_NAME }
    val fingerprint: String get() = Core.fingerprint(publicKey)

    // ByteArray compares by reference, so peers are indexed by this hex string.
    val keyHex: String = publicKey.joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

/** Implemented by the platform: Keychain on Apple, secret service on Linux. */
interface KeyStore {
    fun loadOrCreate(): Identity
    fun setDisplayName(name: String)
}

/** Signs challenges with the permanent key; the private key never leaves. */
interface Signer {
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}
