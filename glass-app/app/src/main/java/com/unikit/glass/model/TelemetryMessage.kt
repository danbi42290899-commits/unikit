package com.unikit.glass.model

import org.json.JSONException
import org.json.JSONObject

/**
 * What a /ws/telemetry frame turned out to be, after just enough parsing
 * to route it. Phase 2's HUD only renders from GlassState (the shared
 * source of truth -- see docs/architecture.md), so `vitals` / `raw_signal`
 * / `device_status` / `event` frames are recognized (they still count as
 * "a message arrived" for the connection-liveness watchdog) but are not
 * deep-parsed. This keeps Glass from doing needless work parsing PPG/ECG
 * sample arrays it doesn't render, and keeps logs free of raw signal
 * spam.
 */
sealed class TelemetryMessage {
    data class GlassState(val state: GlassStateMessage) : TelemetryMessage()
    data class Other(val type: String) : TelemetryMessage()
}

object TelemetryMessageParser {
    private const val TAG = "TelemetryParser"

    fun parse(raw: String): TelemetryMessage? {
        return try {
            val json = JSONObject(raw)
            when (val type = json.optString("type")) {
                "glass_state" -> TelemetryMessage.GlassState(GlassStateMessage.fromJson(json))
                else -> TelemetryMessage.Other(type)
            }
        } catch (e: JSONException) {
            android.util.Log.w(TAG, "failed to parse telemetry frame: ${e.message}")
            null
        }
    }
}
