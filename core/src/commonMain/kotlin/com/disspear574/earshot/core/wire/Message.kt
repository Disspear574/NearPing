package com.disspear574.earshot.core.wire

import com.disspear574.earshot.core.Core
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Подтверждение: дошло до машины, затем человек увидел. */
enum class AckState { DELIVERED, SEEN }

@Serializable
sealed interface Message {

    /** Представление при открытии соединения. */
    @Serializable
    @SerialName("hello")
    data class Hello(val publicKey: String, val displayName: String) : Message

    /** Вызов: 32 случайных байта, одноразовые. */
    @Serializable
    @SerialName("auth_challenge")
    data class AuthChallenge(val challenge: String) : Message

    /**
     * Ответ на вызов: эфемерный ключ обмена и подпись над `challenge ‖ ephemeralKey`
     * постоянным ключом личности. Подпись связывает эфемерный ключ с личностью —
     * без неё посредник подменил бы ключ и слушал обе стороны.
     */
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

    /** Отказ показывается отправителю: иначе он будет слать запрос снова. */
    @Serializable
    @SerialName("pair_reject")
    data object PairReject : Message

    @Serializable
    @SerialName("ping")
    data class Ping(val id: String, val urgent: Boolean) : Message

    @Serializable
    @SerialName("ack")
    data class Ack(val id: String, val state: AckState) : Message

    /** Пауза едет по доверенному соединению, а не в TXT: её видят только контакты. */
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
