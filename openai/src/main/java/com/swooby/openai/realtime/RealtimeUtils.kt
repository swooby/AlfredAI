package com.swooby.openai.realtime

object RealtimeUtils {
    /**
     * Generates an id to send with events and messages
     * @param prefix The prefix to use
     * @param length The length of the id to generate, including the prefix
     * @return The generated id with the given prefix and length
     */
    fun generateId(prefix: String = "evt_", length: Int = 21): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        require(prefix.length <= length) {
            "Prefix length cannot exceed the total length."
        }
        val neededLength = length - prefix.length
        val randomStr = (1..neededLength)
            .map {
                chars.random()
            }
            .joinToString("")
        return prefix + randomStr
    }
}