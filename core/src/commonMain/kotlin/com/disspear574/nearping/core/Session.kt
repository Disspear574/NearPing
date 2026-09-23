package com.disspear574.nearping.core

import com.disspear574.nearping.core.wire.Framing
import com.disspear574.nearping.core.wire.Message
import com.disspear574.nearping.core.wire.Wire

interface SessionEvents {
    fun onEstablished(peer: Peer)
    fun onMessage(peer: Peer, message: Message)
    fun onFailed(reason: String)
}

/**
 * Одно соединение: рукопожатие, вывод общего ключа, запечатывание кадров.
 *
 * Протокол симметричен — обе стороны сразу шлют представление и свой вызов,
 * поэтому неважно, кто соединился первым.
 */
class Session(
    private val identity: Identity,
    private val signer: Signer,
    private val crypto: Crypto,
    private val registry: PeerRegistry,
    private val send: (ByteArray) -> Unit,
    private val events: SessionEvents,
) {
    private val parser = Framing.Parser()

    private var peer: Peer? = null
    private var myChallenge: ByteArray? = null
    private var challengeSpent = false
    private var myEphemeral: EphemeralKeyPair? = null
    private var peerEphemeralKey: ByteArray? = null
    private var theirChallenge: ByteArray? = null
    private var sessionKey: ByteArray? = null
    private var failed = false

    val isEstablished: Boolean get() = sessionKey != null

    /** Вызывается сразу после установки TCP-соединения, с обеих сторон. */
    fun start() {
        val challenge = crypto.randomBytes(CHALLENGE_BYTES)
        myChallenge = challenge
        writePlain(Message.Hello(Base64Url.encode(identity.publicKey), identity.displayName))
        writePlain(Message.AuthChallenge(Base64Url.encode(challenge)))
    }

    fun onBytes(chunk: ByteArray) {
        if (failed) return
        val frames = try {
            parser.feed(chunk)
        } catch (e: IllegalArgumentException) {
            return fail("bad framing: ${e.message}")
        }
        for (frame in frames) {
            if (failed) return
            handleFrame(frame)
        }
    }

    /** Отправляет сообщение по установленному соединению. До рукопожатия молчит. */
    fun sendMessage(message: Message): Boolean {
        val key = sessionKey ?: return false
        send(Framing.frame(crypto.seal(key, Wire.encode(message))))
        return true
    }

    private fun handleFrame(frame: ByteArray) {
        val key = sessionKey
        val plaintext = if (key == null) frame else crypto.open(key, frame)
            ?: return fail("sealed frame did not open")
        val message = try {
            Wire.decode(plaintext)
        } catch (e: IllegalArgumentException) {
            return fail("undecodable message: ${e.message}")
        }
        if (key != null) return deliver(message)

        when (message) {
            is Message.Hello -> onHello(message)
            is Message.AuthChallenge -> onChallenge(message)
            is Message.AuthResponse -> onResponse(message)
            // До рукопожатия ничего содержательного принимать нельзя: личность
            // собеседника ещё не подтверждена, и верить сообщению не на чем.
            else -> fail("message before handshake: $message")
        }
    }

    private fun onHello(hello: Message.Hello) {
        if (peer != null) return fail("duplicate hello")
        val key = Base64Url.decode(hello.publicKey)
        if (key.size != IDENTITY_KEY_BYTES) return fail("bad public key length: ${key.size}")
        if (key.contentEquals(identity.publicKey)) return fail("connected to self")
        peer = Peer(key, hello.displayName)
    }

    private fun onChallenge(challenge: Message.AuthChallenge) {
        if (theirChallenge != null) return fail("duplicate challenge")
        val bytes = Base64Url.decode(challenge.challenge)
        if (bytes.size != CHALLENGE_BYTES) return fail("bad challenge length: ${bytes.size}")
        theirChallenge = bytes

        val ephemeral = crypto.createEphemeralKeyPair()
        myEphemeral = ephemeral
        val signed = bytes + ephemeral.publicKey
        writePlain(
            Message.AuthResponse(
                challenge = challenge.challenge,
                ephemeralKey = Base64Url.encode(ephemeral.publicKey),
                signature = Base64Url.encode(signer.sign(signed)),
            ),
        )
        maybeEstablish()
    }

    private fun onResponse(response: Message.AuthResponse) {
        val them = peer ?: return fail("auth response before hello")
        val mine = myChallenge ?: return fail("auth response without challenge")
        if (challengeSpent) return fail("challenge replayed")

        val echoed = Base64Url.decode(response.challenge)
        if (!echoed.contentEquals(mine)) return fail("challenge does not match")

        val ephemeralKey = Base64Url.decode(response.ephemeralKey)
        if (ephemeralKey.size != EPHEMERAL_KEY_BYTES) {
            return fail("bad ephemeral key length: ${ephemeralKey.size}")
        }
        val signed = mine + ephemeralKey
        if (!signer.verify(signed, Base64Url.decode(response.signature), them.publicKey)) {
            return fail("signature does not verify")
        }
        // Вызов одноразовый: иначе подслушанную пару вызов-ответ можно предъявить снова.
        challengeSpent = true
        peerEphemeralKey = ephemeralKey
        maybeEstablish()
    }

    private fun maybeEstablish() {
        if (sessionKey != null) return
        val them = peer ?: return
        val mine = myEphemeral ?: return
        val theirs = peerEphemeralKey ?: return
        val myChallengeBytes = myChallenge ?: return
        val theirChallengeBytes = theirChallenge ?: return

        val shared = crypto.sharedSecret(mine.privateKey, theirs)
        // Соль должна совпасть на обеих сторонах, а «свой» и «чужой» вызовы у них
        // поменяны местами — поэтому порядок задаётся сравнением, а не ролью.
        val salt = if (compare(myChallengeBytes, theirChallengeBytes) <= 0) {
            myChallengeBytes + theirChallengeBytes
        } else {
            theirChallengeBytes + myChallengeBytes
        }
        sessionKey = crypto.deriveSessionKey(shared, salt)
        events.onEstablished(them)
    }

    private fun deliver(message: Message) {
        val them = peer ?: return fail("message without identity")
        // Барьер доверия: видеть в сети можно всех, вызывать — только принявших запрос.
        if (message is Message.Ping && !registry.isTrusted(them.publicKey)) return
        events.onMessage(them, message)
    }

    private fun writePlain(message: Message) {
        send(Framing.frame(Wire.encode(message)))
    }

    private fun fail(reason: String) {
        if (failed) return
        failed = true
        events.onFailed(reason)
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    private companion object {
        const val CHALLENGE_BYTES = 32
        const val IDENTITY_KEY_BYTES = 32
        const val EPHEMERAL_KEY_BYTES = 32
    }
}
