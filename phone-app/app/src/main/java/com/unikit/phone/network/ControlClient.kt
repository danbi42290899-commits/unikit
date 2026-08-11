package com.unikit.phone.network

import android.util.Log
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Sends commands on /ws/control and listens for the ack/error that comes
 * back on the same connection (see docs/websocket_spec.md -- command
 * replies are not broadcast on /ws/telemetry, they're a direct reply here).
 *
 * Ported from glass-app/network/ControlClient.kt with one small, deliberate
 * extension: the CAPTURE ack can carry extra fields beyond ok/command --
 * `mediaId` on success or `reason` on failure (see server/app/ws/control.py
 * `_persist_capture()`) -- and OtoscopeFragment/ColposcopeFragment need
 * that detail to show a meaningful toast ("CAPTURE SAVED (M1234ABCD)" vs.
 * "no live camera frame yet"), not just ok/fail. Glass's ControlClient
 * never needed this because its own CAPTURE handling only cares about
 * ok/fail (see UniHubRepository.captureResults there). Same "assume the
 * next ack belongs to the last command" simplification as the Glass
 * version -- no per-command correlation id, not needed with the current
 * one-button-at-a-time UI flows.
 */
class ControlClient(
    httpClient: OkHttpClient,
    private val onAck: (command: String, ok: Boolean, detail: Map<String, String>) -> Unit,
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
                val detail = HashMap<String, String>()
                json.keys().forEach { key ->
                    if (key != "type" && key != "command" && key != "ok") {
                        detail[key] = json.optString(key)
                    }
                }
                Log.i(TAG, "$command acknowledged (ok=$ok, detail=$detail)")
                onAck(command, ok, detail)
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
