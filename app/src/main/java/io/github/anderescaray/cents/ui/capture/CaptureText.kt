package io.github.anderescaray.cents.ui.capture

/**
 * What of a notification capture reads, and what it has already read.
 *
 * Kept free of Android so both rules are JVM tests: the listener that uses them can only be
 * checked on a phone.
 */
object CaptureText {
    /**
     * The title and the fullest body a notification carries, as one line.
     *
     * The expanded text wins over the short one when there is one, because a bank that puts the
     * merchant in the title often cuts the amount off the collapsed line. Null when there is
     * nothing to read, which is most media and progress notifications.
     */
    fun of(
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence?,
    ): String? {
        val body = bigText?.takeIf { it.isNotBlank() } ?: text
        return listOfNotNull(title, body)
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifEmpty { null }
    }
}

/**
 * Remembers which notifications were already handed on, by their key and their text.
 *
 * Android posts a notification again when an app updates it, often with the same words, and
 * some apps repost the same one every time the phone wakes. The same key with the same text is
 * the same notification; the same key with new text is news. Bounded, so it never grows past
 * [capacity] entries however long the phone stays on.
 */
class SeenNotifications(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val seen =
        object : LinkedHashMap<String, String>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > capacity
        }

    /** True the first time this key is seen with this text, false for a repost of it. */
    @Synchronized
    fun isNew(
        key: String,
        text: String,
    ): Boolean {
        val before = seen.put(key, text)
        return before != text
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
        const val LOAD_FACTOR = 0.75f
    }
}
