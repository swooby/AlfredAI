package com.swooby.alfredai.openai.realtime

open class RealtimeEventHandler {
    private val eventHandlers = mutableMapOf<String, MutableList<(Any) -> Unit>>()
//    private val nextEventHandlers = mutableMapOf<String, MutableList<(Any) -> Unit>>()

    /**
     * Clears all event handlers.
     */
    fun clearEventHandlers() {
        eventHandlers.clear()
//        nextEventHandlers.clear()
    }

    /**
     * Listen to specific events.
     * @param eventName The name of the event to listen to.
     * @param callback The callback function to execute when the event is triggered.
     */
    fun on(eventName: String, callback: (Any) -> Unit): (Any) -> Unit {
        eventHandlers.getOrPut(eventName) { mutableListOf() }.add(callback)
        return callback;
    }

//    fun onNext(eventName: String, callback: (Any) -> Unit): (Any) -> Unit {
//        nextEventHandlers.getOrPut(eventName) { mutableListOf() }.add(callback)
//        return callback;
//    }

    fun off(eventName: String, callback: ((Any) -> Unit)? = null) {
        if (callback != null) {
            eventHandlers[eventName]?.remove(callback)
        } else {
            eventHandlers[eventName]?.clear()
        }
    }

//    fun offNext(eventName: String, callback: ((Any) -> Unit)? = null) {
//        if (callback != null) {
//            nextEventHandlers[eventName]?.remove(callback)
//        } else {
//            nextEventHandlers[eventName]?.clear()
//        }
//    }

//    suspend fun waitForNext(eventName: String, timeout: Long? = null): Any? {
//
//    }

    fun dispatch(eventName: String, data: Any) {
        eventHandlers[eventName]?.forEach { it(data) }
//        nextEventHandlers[eventName]?.forEach { it(data) }
//        nextEventHandlers.remove(eventName)
    }
}