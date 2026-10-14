package com.shlomoa.myadhdpal.protocol

/** A protocol version `major.minor` (a patch part is accepted and ignored). */
data class ProtocolVersion(val major: Int, val minor: Int) {
    companion object {
        private val pattern = Regex("(\\d{1,4})\\.(\\d{1,4})(\\.\\d{1,4})?")

        /** Returns null for a missing or malformed version; never throws. */
        fun parse(text: String?): ProtocolVersion? {
            val match = text?.let { pattern.matchEntire(it) } ?: return null
            return ProtocolVersion(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}

/** The compatibility rule of `docs/protocol.md`: only the major version matters. */
sealed interface Compatibility {
    data object Compatible : Compatibility

    /** The receiver ignores the item (or, for a hello, shows the Incompatible link state). */
    data class Incompatible(val reason: String) : Compatibility

    companion object {
        fun ofMajor(major: Int): Compatibility =
            if (major == Protocol.MAJOR_VERSION) Compatible else Incompatible("unsupported major version")

        /** A missing or malformed hello version is Incompatible. */
        fun ofHello(protocolVersion: String?): Compatibility {
            val version = ProtocolVersion.parse(protocolVersion) ?: return Incompatible("missing or malformed version")
            return ofMajor(version.major)
        }
    }
}
