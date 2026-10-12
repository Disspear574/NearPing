package com.disspear574.nearping.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

/** Linux identity: Ed25519 key in a 0600 file; file permissions are the protection the OS offers. */
class FileIdentityStore(private val directory: Path) : KeyStore {
    private val keyFile: Path = directory.resolve("identity.key")
    private val nameFile: Path = directory.resolve("display-name")

    private val privateKey: ByteArray by lazy { loadOrCreateKey() }

    val signer: Signer by lazy { JvmSigner(privateKey) }

    override fun loadOrCreate(): Identity {
        val public = ed25519PublicKey(privateKey)
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

/** Trusted peers in a file next to the key. */
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
        // A name with a newline or tab would break the one-line-per-record format.
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
