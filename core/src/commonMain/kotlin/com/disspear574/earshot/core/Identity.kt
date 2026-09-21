package com.disspear574.earshot.core

/**
 * Личность устройства. Приватная часть ключа живёт в хранилище платформы и сюда не попадает.
 * Имя задаёт пользователь; адрес и имя машины личностью не являются — она держится на ключе.
 */
class Identity(
    val publicKey: ByteArray,
    displayName: String,
) {
    val displayName: String = displayName.trim().ifBlank { FALLBACK_NAME }
    val fingerprint: String get() = Core.fingerprint(publicKey)

    companion object {
        const val FALLBACK_NAME = "Earshot"
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
