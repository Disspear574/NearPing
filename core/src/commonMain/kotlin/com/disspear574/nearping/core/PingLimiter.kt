package com.disspear574.nearping.core

/** Per-peer limits; rejected attempts never extend the cooldown. */
internal class PingLimiter {
    private data class State(val recent: MutableList<Long> = mutableListOf(), var until: Long = 0)
    private val states = mutableMapOf<String, State>()
    fun remaining(key: String, urgent: Boolean, now: Long): Long =
        ((states["$key:$urgent"]?.until ?: 0) - now).coerceAtLeast(0)
    fun record(key: String, urgent: Boolean, now: Long) {
        val state = states.getOrPut("$key:$urgent") { State() }
        state.recent.removeAll { it <= now - 60 }
        state.recent.add(now)
        // Urgent remains reachable during a normal timeout, once every 30 seconds.
        state.until = now + if (urgent || state.recent.size >= 5) 30 else 3
    }
}
