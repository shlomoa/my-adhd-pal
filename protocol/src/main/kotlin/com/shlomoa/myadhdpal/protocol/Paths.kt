package com.shlomoa.myadhdpal.protocol

/** A parsed item path. */
sealed interface ItemPath {
    data object Schedule : ItemPath
    data class Prompt(val id: String) : ItemPath
    data class Ack(val id: String) : ItemPath
    data class Selection(val id: String) : ItemPath
    data class Alert(val id: String) : ItemPath
    data class Hello(val side: Side) : ItemPath
    data object DebugBiometrics : ItemPath
}

/** Path builders and parser. The catalog in `docs/protocol.md` is the specification. */
object Paths {
    const val SCHEDULE = "/schedule"
    const val HELLO_PHONE = "/hello/phone"
    const val HELLO_WATCH = "/hello/watch"
    const val DEBUG_BIOMETRICS = "/debug/biometrics"
    const val PROMPT_PREFIX = "/prompt/"
    const val ACK_PREFIX = "/ack/"
    const val SELECTION_PREFIX = "/selection/"
    const val ALERT_PREFIX = "/alert/"

    private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** An id is a lower-case canonical UUID. */
    fun isValidId(id: String): Boolean = idPattern.matches(id)

    fun prompt(id: String): String = PROMPT_PREFIX + checked(id)

    fun ack(id: String): String = ACK_PREFIX + checked(id)

    fun selection(id: String): String = SELECTION_PREFIX + checked(id)

    fun alert(id: String): String = ALERT_PREFIX + checked(id)

    /** The path under which [message] is stored; [id] is used by the event kinds. */
    fun pathFor(id: String, message: Message): String = when (message) {
        is Schedule -> SCHEDULE
        is Prompt -> prompt(id)
        is Acknowledgement -> ack(id)
        is Selection -> selection(id)
        is Alert -> alert(id)
        is Hello -> if (message.side == Side.PHONE) HELLO_PHONE else HELLO_WATCH
        is BiometricSamples -> DEBUG_BIOMETRICS
    }

    /** Returns null for unknown paths, empty or invalid ids, trailing slashes and extra segments. */
    fun parse(path: String): ItemPath? = when (path) {
        SCHEDULE -> ItemPath.Schedule
        HELLO_PHONE -> ItemPath.Hello(Side.PHONE)
        HELLO_WATCH -> ItemPath.Hello(Side.WATCH)
        DEBUG_BIOMETRICS -> ItemPath.DebugBiometrics
        else -> parseEvent(path)
    }

    private fun parseEvent(path: String): ItemPath? {
        val kinds = listOf<Pair<String, (String) -> ItemPath>>(
            PROMPT_PREFIX to { ItemPath.Prompt(it) },
            ACK_PREFIX to { ItemPath.Ack(it) },
            SELECTION_PREFIX to { ItemPath.Selection(it) },
            ALERT_PREFIX to { ItemPath.Alert(it) }
        )
        for ((prefix, make) in kinds) {
            if (!path.startsWith(prefix)) continue
            val id = path.substring(prefix.length)
            return if (isValidId(id)) make(id) else null
        }
        return null
    }

    private fun checked(id: String): String {
        require(isValidId(id)) { "invalid item id" }
        return id
    }
}
