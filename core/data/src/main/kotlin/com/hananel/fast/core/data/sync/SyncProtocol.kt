package com.hananel.fast.core.data.sync

/**
 * The names both apps use on the Wearable Data Layer.
 *
 * The watch writes each finished session as a data item at [sessionPath] with
 * the packed session attached. The phone stores it and answers with an item at
 * [ackPath]. Only then does the watch consider the session safe: "sent" proves
 * nothing, "stored on the phone" does.
 */
object SyncProtocol {

    const val SESSION_PREFIX = "/fast/session/"
    const val ACK_PREFIX = "/fast/ack/"

    /**
     * Written by a phone that has lost its sessions — a reinstall, cleared data —
     * to ask the watch for everything again. The watch then forgets which sessions
     * were confirmed and offers them all; the phone keeps one copy of each.
     */
    const val RESYNC_PATH = "/fast/resync"

    /**
     * Messages, not data items: the watch saying it has started or stopped measuring,
     * so that a phone set to measure together with it does the same (D43).
     */
    const val LINK_PREFIX = "/fast/link/"
    const val LINK_START_PATH = LINK_PREFIX + "start"
    const val LINK_STOP_PATH = LINK_PREFIX + "stop"

    /**
     * The one byte a stop carries: whether the watch really measured (a training was started,
     * a speed test counted down) or only searched for GPS and was left. A stop without it comes
     * from a watch older than 0.6.2 and counts as measured.
     */
    const val STOP_MEASURED: Byte = 1
    const val STOP_SEARCH_ONLY: Byte = 0

    /**
     * The phone inviting the watch to a joint run (D47): the phone is already measuring, and
     * the watch shows a notice that opens a run with one tap.
     */
    const val LINK_INVITE_PATH = LINK_PREFIX + "invite"

    /** The phone's answers to a start: it is measuring too, or it wanted to and could not. */
    const val LINK_FOLLOWING_PATH = LINK_PREFIX + "following"
    const val LINK_NOT_FOLLOWING_PATH = LINK_PREFIX + "not-following"

    /** The packed session, as an asset. */
    const val KEY_PAYLOAD = "payload"

    /**
     * When this item was last written. A data item rewritten with identical content
     * is not delivered again, so the timestamp is what lets either side repeat itself
     * after the other has lost its copy.
     */
    const val KEY_WRITTEN_AT = "writtenAt"

    fun sessionPath(id: String): String = SESSION_PREFIX + id

    fun ackPath(id: String): String = ACK_PREFIX + id

    /** The session id a path refers to, or null when the path is not under [prefix]. */
    fun idFrom(path: String, prefix: String): String? =
        path.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotEmpty() && '/' !in it }
}
