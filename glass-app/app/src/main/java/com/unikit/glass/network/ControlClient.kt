package com.unikit.glass.network

import android.util.Log
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Sends commands on /ws/control and listens for the ack/error that comes
 * back on the same connection (see docs/websocket_spec.md -- command
 * replies are not broadcast on /ws/telemetry, they're a direct reply here).
 *
 * Phase 2 only ever sends CAPTURE, so "the next reply belongs to my last
 * command" is a safe simplification. If a later phase sends multiple
 * command types in flight concurrently, this would need per-command
 * correlation (e.g. a client-generated request id echoed back) -- not
 * needed yet.
 */
class ControlClient(
    httpClient: OkHttpClient,
    private val onAck: (command: String, ok: Boolean) -> Unit,
    private val onError: (message: String) -> Unit,
) : ReconnectingWebSocketClient(httpClient, UniHubConfig.controlUrl, TAG) {

    fun sendCommand(command: String): Boolean {
        val sent = send(JSONObject(mapOf("command" to command)).toString())
        if (sent) {
            Log.i(TAG, "$command sent")
        } else {
            Log.w(TAG, "$command NOT sent: control socket not connected")
        }
        return sent
    }

    override fun onMessageReceived(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: org.json.JSONException) {
            Log.w(TAG, "failed to parse control reply: ${e.message}")
            return
        }
        when (json.optString("type")) {
            "command_ack" -> {
                val command = json.optString("command")
                val ok = json.optBoolean("ok", false)
                Log.i(TAG, "$command acknowledged (ok=$ok)")
                onAck(command, ok)
            }
            "error" -> {
                val message = json.optString("message")
                Log.w(TAG, "control error: $message")
                onError(message)
            }
        }
    }

    companion object {
        private const val TAG = "ControlClient"
    }
}
