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
        // absent optional fields are not written, so a 1.0 reader sees the body it knows
        explicitNulls = false
    }

    fun encode(id: String, timestamp: Long, message: Message): Envelope =
        Envelope(Protocol.MAJOR_VERSION, id, timestamp, encodeBody(message))

    fun encodeBody(message: Message): String = when (message) {
        is Schedule -> json.encodeToString(Schedule.serializer(), message)
        is Prompt -> json.encodeToString(Prompt.serializer(), message)
        is Acknowledgement -> json.encodeToString(Acknowledgement.serializer(), message)
        is Selection -> json.encodeToString(Selection.serializer(), message)
        is Alert -> json.encodeToString(Alert.serializer(), message)
        is EpisodeEnd -> json.encodeToString(EpisodeEnd.serializer(), message)
        is WatchStatus -> json.encodeToString(WatchStatus.serializer(), message)
        is Hello -> json.encodeToString(Hello.serializer(), message)
        is BiometricSamples -> json.encodeToString(BiometricSamples.serializer(), message)
    }

    /** Like [decode], but an item whose major version is not supported is ignored ([DecodeResult.Failure]). */
    fun decodeIfCompatible(path: String, envelope: Envelope): DecodeResult {
        val compatibility = Compatibility.ofMajor(envelope.version)
        if (compatibility is Compatibility.Incompatible) return DecodeResult.Failure(compatibility.reason)
        return decode(path, envelope)
    }

    /** Decodes the body of the item stored at [path]. The major version is checked by [decodeIfCompatible]. */
    fun decode(path: String, envelope: Envelope): DecodeResult {
        val kind = Paths.parse(path) ?: return DecodeResult.Failure("unknown path")
        val serializer: KSerializer<out Message> = when (kind) {
            ItemPath.Schedule -> Schedule.serializer()
            is ItemPath.Prompt -> Prompt.serializer()
            is ItemPath.Ack -> Acknowledgement.serializer()
            is ItemPath.Selection -> Selection.serializer()
            is ItemPath.Alert -> Alert.serializer()
            is ItemPath.EpisodeEnd -> EpisodeEnd.serializer()
            ItemPath.WatchStatus -> WatchStatus.serializer()
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
