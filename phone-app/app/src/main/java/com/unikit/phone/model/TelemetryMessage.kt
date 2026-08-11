package com.unikit.phone.model

import org.json.JSONException
import org.json.JSONObject

/**
 * What a /ws/telemetry frame turned out to be, after just enough parsing
 * to route it. Extended from glass-app's TelemetryMessage: Glass only
 * ever needed `glass_state` (its single source of truth), but Phone's
 * dashboard/vitals/device-status/waveform screens need `vitals`,
 * `device_status`, `raw_signal`, and `event` deep-parsed too --
 * following the exact same sealed-subtype + parser pattern already used
 * for `glass_state`.
 */
sealed class TelemetryMessage {
    data class GlassState(val state: GlassStateMessage) : TelemetryMessage()
    data class Vitals(val vitals: VitalsSnapshot) : TelemetryMessage()
    data class DeviceStatus(val status: DeviceStatusInfo) : TelemetryMessage()
    data class RawSignal(val signal: RawSignalSnapshot) : TelemetryMessage()
    data class Event(val event: EventLogEntry) : TelemetryMessage()
    data class Other(val type: String) : TelemetryMessage()
}

object TelemetryMessageParser {
    private const val TAG = "TelemetryParser"

    fun parse(raw: String): TelemetryMessage? {
        return try {
            val json = JSONObject(raw)
            when (val type = json.optString("type")) {
                "glass_state" -> TelemetryMessage.GlassState(GlassStateMessage.fromJson(json))
                "vitals" -> TelemetryMessage.Vitals(VitalsSnapshot.fromJson(json))
                "device_status" -> TelemetryMessage.DeviceStatus(DeviceStatusInfo.fromJson(json))
                "raw_signal" -> TelemetryMessage.RawSignal(RawSignalSnapshot.fromJson(json))
                "event" -> TelemetryMessage.Event(EventLogEntry.fromJson(json))
                else -> TelemetryMessage.Other(type)
            }
        } catch (e: JSONException) {
            android.util.Log.w(TAG, "failed to parse telemetry frame: ${e.message}")
            null
        }
    }
}
