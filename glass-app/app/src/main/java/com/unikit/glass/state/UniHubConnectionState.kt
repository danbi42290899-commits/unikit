package com.unikit.glass.state

/**
 * Connection lifecycle for a single WebSocket link to UNI-HUB.
 *
 * CONNECTING     -- first-ever connect attempt, nothing has succeeded yet.
 * CONNECTED      -- socket open, messages can flow.
 * DISCONNECTED   -- link just dropped; a retry is scheduled but not yet
 *                   firing (this is what should blank out HUD values).
 * RECONNECTING   -- a retry connect() call is actively in flight.
 */
enum class UniHubConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
}
