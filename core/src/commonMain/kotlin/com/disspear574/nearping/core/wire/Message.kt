package com.disspear574.nearping.core.wire

import com.disspear574.nearping.core.Core
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Delivery acknowledgement; RATE_LIMITED means the receiver dropped the call. */
enum class AckState { DELIVERED, SEEN, RATE_LIMITED }

@Serializable
sealed interface Message {

    /** Greeting sent when a connection opens. */
    @Serializable
    @SerialName("hello")
    data class Hello(val publicKey: String, val displayName: String) : Message

    /** Single-use 32-byte challenge. */
    @Serializable
    @SerialName("auth_challenge")
    data class AuthChallenge(val challenge: String) : Message

    /** Challenge response: ephemeral key plus a signature binding it to the identity, against a man-in-the-middle. */
    @Serializable
    @SerialName("auth_response")
    data class AuthResponse(
        val challenge: String,
        val ephemeralKey: String,
        val signature: String,
    ) : Message

    @Serializable
    @SerialName("pair_request")
    data class PairRequest(val displayName: String) : Message

    @Serializable
    @SerialName("pair_accept")
    data object PairAccept : Message

    /** Pair refusal, shown to the sender so it stops re-sending. */
    @Serializable
    @SerialName("pair_reject")
    data object PairReject : Message

    @Serializable
    @SerialName("ping")
    data class Ping(val id: String, val urgent: Boolean) : Message

    @Serializable
    @SerialName("ack")
    data class Ack(val id: String, val state: AckState) : Message

    /** Pause state, sent over the trusted connection so only contacts see it. */
    @Serializable
    @SerialName("presence")
    data class Presence(
        val pausedUntilEpochSeconds: Long? = null,
        val heartbeat: Boolean = false,
    ) : Message
}

@Serializable
private data class Envelope(val v: Int, val body: Message)

object Wire {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(message: Message): ByteArray =
        json.encodeToString(Envelope(Core.protocolVersion, message)).encodeToByteArray()

    fun decode(bytes: ByteArray): Message {
        val envelope = json.decodeFromString<Envelope>(bytes.decodeToString())
        require(envelope.v == Core.protocolVersion) {
            "protocol version ${envelope.v}, expected ${Core.protocolVersion}"
        }
        return envelope.body
    }
}
