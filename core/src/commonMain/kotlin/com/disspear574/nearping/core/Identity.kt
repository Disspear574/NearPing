package com.disspear574.nearping.core

/**
 * Личность устройства. Приватная часть ключа живёт в хранилище платформы и сюда не попадает.
 * Имя задаёт пользователь; адрес и имя машины личностью не являются — она держится на ключе.
 */
class Identity(
    val publicKey: ByteArray,
    displayName: String,
) {
    /** Имя меняется на лету: ключ остаётся, сессии переподнимаются с новым `hello`. */
    var displayName: String = normalize(displayName)
        set(value) { field = normalize(value) }

    val fingerprint: String get() = Core.fingerprint(publicKey)

    private fun normalize(name: String): String = name.trim().ifBlank { FALLBACK_NAME }

    companion object {
        const val FALLBACK_NAME = "NearPing"
    }
}

/** Другая машина. Узнаётся по ключу, а не по имени или адресу. */
class Peer(
    val publicKey: ByteArray,
    displayName: String,
) {
    val displayName: String = displayName.trim().ifBlank { Identity.FALLBACK_NAME }
    val fingerprint: String get() = Core.fingerprint(publicKey)

    /** Ключ строкой — им индексируются пиры: ByteArray сравнивается по ссылке. */
    val keyHex: String = publicKey.joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

/** Реализуется платформой: Keychain на Apple, secret service на Linux. */
interface KeyStore {
    fun loadOrCreate(): Identity
    fun setDisplayName(name: String)
}

/** Подпись челленджа постоянным ключом. Приватный ключ наружу не отдаётся. */
interface Signer {
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}
