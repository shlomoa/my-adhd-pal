package com.shlomoa.myadhdpal.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The four keys of a Data Layer item, without any Android type. */
data class Envelope(val version: Int, val id: String, val timestamp: Long, val body: String)

/** A decoded item. */
data class Item(val id: String, val timestamp: Long, val message: Message)

sealed interface DecodeResult {
    data class Ok(val item: Item) : DecodeResult

    /** The item is dropped. [reason] is for logging in debug builds only; it never contains the body. */
    data class Failure(val reason: String) : DecodeResult
}

/** Encodes and decodes item bodies. [decode] never throws. */
object Codec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(id: String, timestamp: Long, message: Message): Envelope =
        Envelope(Protocol.MAJOR_VERSION, id, timestamp, encodeBody(message))

    fun encodeBody(message: Message): String = when (message) {
        is Schedule -> json.encodeToString(Schedule.serializer(), message)
        is Prompt -> json.encodeToString(Prompt.serializer(), message)
        is Acknowledgement -> json.encodeToString(Acknowledgement.serializer(), message)
        is Selection -> json.encodeToString(Selection.serializer(), message)
        is Alert -> json.encodeToString(Alert.serializer(), message)
        is Hello -> json.encodeToString(Hello.serializer(), message)
        is BiometricSamples -> json.encodeToString(BiometricSamples.serializer(), message)
    }

    /** Decodes the body of the item stored at [path]. The major version is checked separately (step 1.4). */
    fun decode(path: String, envelope: Envelope): DecodeResult {
        val kind = Paths.parse(path) ?: return DecodeResult.Failure("unknown path")
        val serializer: KSerializer<out Message> = when (kind) {
            ItemPath.Schedule -> Schedule.serializer()
            is ItemPath.Prompt -> Prompt.serializer()
            is ItemPath.Ack -> Acknowledgement.serializer()
            is ItemPath.Selection -> Selection.serializer()
            is ItemPath.Alert -> Alert.serializer()
            is ItemPath.Hello -> Hello.serializer()
            ItemPath.DebugBiometrics -> BiometricSamples.serializer()
        }
        return try {
            DecodeResult.Ok(Item(envelope.id, envelope.timestamp, json.decodeFromString(serializer, envelope.body)))
        } catch (e: SerializationException) {
            DecodeResult.Failure("invalid body: ${e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            DecodeResult.Failure("invalid body: ${e.javaClass.simpleName}")
        }
    }
}
