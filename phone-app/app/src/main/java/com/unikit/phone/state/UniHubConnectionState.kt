package com.unikit.phone.state

/**
 * Connection lifecycle for a single WebSocket link to UNI-HUB. Ported
 * verbatim from glass-app/state/UniHubConnectionState.kt.
 *
 * CONNECTING     -- first-ever connect attempt, nothing has succeeded yet.
 * CONNECTED      -- socket open, messages can flow.
 * DISCONNECTED   -- link just dropped; a retry is scheduled but not yet
 *                   firing (this is what should blank out stale values).
 * RECONNECTING   -- a retry connect() call is actively in flight.
 */
enum class UniHubConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
}
