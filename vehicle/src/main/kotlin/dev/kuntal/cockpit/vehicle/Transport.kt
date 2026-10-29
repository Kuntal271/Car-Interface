package dev.kuntal.cockpit.vehicle

interface Transport {
    suspend fun open()
    suspend fun close()

    /**
     * Send one command (no CR). Return everything received before the '>' prompt, trimmed.
     * Throws IOException on timeout or disconnect.
     */
    suspend fun exchange(cmd: String): String
}
