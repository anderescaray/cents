package io.github.anderescaray.cents.ui.capture

/**
 * What of a notification capture reads, whether to read it at all, and what it has already read.
 *
 * Kept free of Android so every rule here is a JVM test: the listener that uses them can only be
 * checked on a phone.
 */
object CaptureText {
    /**
     * Whether a notification is worth reading: capture is on, the app is one the owner chose, it is
     * not this app's own, and it is not a kind that can never be a payment.
     *
     * Asked **before any of the notification is read**, so nothing of an app nobody chose is ever
     * looked at, let alone kept (D29). Ongoing and foreground-service notifications (music, a
     * download, navigation) update every second and are never a payment; a group's summary repeats
     * the notifications under it.
     */
    fun worthReading(
        packageName: String,
        ownPackage: String,
        chosen: Set<String>,
        enabled: Boolean,
        ongoing: Boolean,
        groupSummary: Boolean,
    ): Boolean = enabled && packageName != ownPackage && packageName in chosen && !ongoing && !groupSummary

    /**
     * The fullest body a notification carries, then its title, as one line.
     *
     * **The body comes first**, because the amount taken is the first one in the text, and a bank
     * that titles its notification with the balance would otherwise have the balance recorded as
     * the payment. The expanded text wins over the short one, which is often cut before the
     * amount; the lines of a notification that bundles several movements are read when there is
     * no expanded text. Null when there is nothing to read.
     */
    fun of(
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence?,
        lines: List<CharSequence>? = null,
    ): String? {
        val bundled = lines?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(". ")
        val body = bigText?.takeIf { it.isNotBlank() } ?: bundled ?: text
        return listOfNotNull(body, title)
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifEmpty { null }
    }
}

/**
 * Remembers which notifications were already handed on: by their key, the time their app gave
 * them, and their text.
 *
 * Android posts a notification again when an app updates it, often with the same words, and
 * some apps repost the same one every time the phone wakes: the same key, time and text is the
 * same notification. A bank that reuses one key for every movement gives each new one a new time,
 * so a second 2,50 € coffee in the afternoon is news, not a repost of the morning's. A key is also
 * forgotten when its notification is dismissed. Bounded, so it never grows past [capacity].
 */
class SeenNotifications(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val seen =
        object : LinkedHashMap<String, String>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > capacity
        }

    /** True the first time this key is seen with this time and text, false for a repost. */
    @Synchronized
    fun isNew(
        key: String,
        postedBy: Long,
        text: String,
    ): Boolean {
        val identity = "$postedBy|$text"
        return seen.put(key, identity) != identity
    }

    /** Forgets a key: its notification was dismissed, or handing it on failed and a repost should try again. */
    @Synchronized
    fun forget(key: String) {
        seen.remove(key)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
        const val LOAD_FACTOR = 0.75f
    }
}
