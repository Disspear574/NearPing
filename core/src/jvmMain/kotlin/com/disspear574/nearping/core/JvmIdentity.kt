package com.disspear574.nearping.core

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

/**
 * Личность под Linux: постоянный ключ Ed25519 в файле с правами 0600.
 *
 * Связки ключей, как на macOS, здесь нет. Права файла — тот уровень защиты,
 * который даёт система: приватный ключ отвечает на вопрос «кто может тебя
 * вызвать в локальной сети», и если чужой код уже выполняется от твоего имени,
 * он с тем же успехом нажмёт кнопку в самом приложении.
 */
class FileIdentityStore(private val directory: Path) : KeyStore {
    private val keyFile: Path = directory.resolve("identity.key")
    private val nameFile: Path = directory.resolve("display-name")

    private val privateKey: ByteArray by lazy { loadOrCreateKey() }

    val signer: Signer by lazy { JvmSigner(privateKey) }

    override fun loadOrCreate(): Identity {
        val public = Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
        return Identity(public, storedName())
    }

    override fun setDisplayName(name: String) {
        Files.createDirectories(directory)
        Files.writeString(nameFile, name)
    }

    private fun storedName(): String {
        val saved = runCatching { Files.readString(nameFile).trim() }.getOrNull()
        if (!saved.isNullOrBlank()) return saved
        return System.getenv("HOSTNAME")
            ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
            ?: ""
    }

    private fun loadOrCreateKey(): ByteArray {
        Files.createDirectories(directory)
        if (Files.exists(keyFile)) {
            val bytes = Files.readAllBytes(keyFile)
            if (bytes.size == KEY_BYTES) return bytes
        }
        val generated = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        Files.write(keyFile, generated)
        restrictToOwner(keyFile)
        return generated
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/** Доверенные пиры в JSON рядом с ключом. */
class JvmPeerStorage(private val directory: Path) : PeerStorage {
    private val file: Path = directory.resolve("peers.tsv")

    override fun load(): List<Peer> {
        if (!Files.exists(file)) return emptyList()
        return Files.readAllLines(file).mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val key = runCatching { parts[0].hexToBytes() }.getOrNull() ?: return@mapNotNull null
            if (key.size != IDENTITY_KEY_BYTES) return@mapNotNull null
            Peer(key, parts[1])
        }
    }

    override fun save(peers: List<Peer>) {
        Files.createDirectories(directory)
        // Имя может содержать что угодно, кроме перевода строки и табуляции —
        // иначе одна строка файла перестанет быть одной записью.
        val lines = peers.map { "${it.keyHex}\t${it.displayName.replace('\t', ' ').replace('\n', ' ')}" }
        Files.write(file, lines)
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val IDENTITY_KEY_BYTES = 32
    }
}

class JvmClock : Clock {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
}
