package com.unikit.phone.model

import org.json.JSONObject

/**
 * Mirrors the server's DeviceStatus (app/models/device.py). Used both for
 * the `device_status` telemetry stream and the `GET /devices` REST
 * snapshot -- same wire shape either way (telemetry just flattens
 * {"type": "device_status", **status}).
 */
data class DeviceStatusInfo(
    val device: String,
    val state: String, // CONNECTED | DISCONNECTED
    val lastSeen: String?,
    val quality: String?,
    val simulated: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject): DeviceStatusInfo = DeviceStatusInfo(
            device = json.optString("device"),
            state = json.optString("state", "DISCONNECTED"),
            lastSeen = json.optNullableString("lastSeen"),
            quality = json.optNullableString("quality"),
            simulated = json.optBoolean("simulated", true),
        )
    }
}
