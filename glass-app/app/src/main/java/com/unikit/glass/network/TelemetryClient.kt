package com.unikit.glass.network

import android.util.Log
import com.unikit.glass.model.GlassStateMessage
import com.unikit.glass.model.TelemetryMessage
import com.unikit.glass.model.TelemetryMessageParser
import okhttp3.OkHttpClient

/**
 * Read-only subscription to /ws/telemetry. Broadcast channel -- Glass
 * never sends anything meaningful on it (see docs/websocket_spec.md).
 */
class TelemetryClient(
    httpClient: OkHttpClient,
    private val onGlassState: (GlassStateMessage) -> Unit,
    private val onAnyMessage: () -> Unit,
) : ReconnectingWebSocketClient(httpClient, UniHubConfig.telemetryUrl, TAG) {

    override fun onMessageReceived(text: String) {
        onAnyMessage()
        when (val message = TelemetryMessageParser.parse(text) ?: return) {
            is TelemetryMessage.GlassState -> {
                Log.d(TAG, "glass_state received (examId=${message.state.examId})")
                onGlassState(message.state)
            }
            is TelemetryMessage.Other -> {
                // vitals / raw_signal / device_status / event: liveness only
                // in Phase 2, intentionally not logged per-message to avoid
                // spamming logs with a stream running several times/sec.
            }
        }
    }

    companion object {
        private const val TAG = "TelemetryClient"
    }
}
