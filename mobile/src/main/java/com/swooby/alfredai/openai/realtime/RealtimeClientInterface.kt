package com.swooby.alfredai.openai.realtime

interface RealtimeClientInterface {
    /**
     * Indicates if the Realtime WebSocket is connected and the session has started.
     * @return Boolean
     */
    val isConnected: Boolean

    /**
     * Resets the client instance entirely: disconnects and clears active config.
     * @return Boolean
     */
    fun reset(): Boolean

    /**
     * Connects to the Realtime WebSocket API and updates session config.
     * @throws Exception if already connected
     * @return True on successful connection
     */
    suspend fun connect(): Boolean

    /**
     * Disconnects from the Realtime API and clears the conversation history.
     */
    fun disconnect()

    /**
     * Gets the active turn detection mode.
     * @return "server_vad" or null
     */
    fun getTurnDetectionType(): String?

    /**
     * Deletes an item from the conversation.
     * @param id ID of the item
     * @return True if successful
     */
    fun deleteItem(id: String): Boolean

    /**
     * Updates the session configuration. If connected, sends "session.update" to the server.
     * @param sessionConfig The new configuration values (optional)
     * @return True if successful
     */
    fun updateSession(sessionConfig: SessionConfig = SessionConfig())

    /**
     * Forces the model to generate a response.
     * @return True on success
     */
    fun createResponse(): Boolean
}