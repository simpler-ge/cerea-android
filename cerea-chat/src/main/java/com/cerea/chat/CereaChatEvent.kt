package com.cerea.chat

/** Something the widget reports to the app. Delivered through [CereaChatFragment.onEvent]. */
enum class CereaChatEvent(internal val type: String) {
    /** The widget has loaded its configuration and theme. */
    READY("ready"),

    /** The chat window is showing. */
    OPEN("open"),

    /**
     * The user tapped the back button in the widget's header. The fragment
     * performs a system back right after reporting it.
     */
    CLOSE("close");

    internal companion object {
        fun fromType(type: String): CereaChatEvent? = entries.firstOrNull { it.type == type }
    }
}
