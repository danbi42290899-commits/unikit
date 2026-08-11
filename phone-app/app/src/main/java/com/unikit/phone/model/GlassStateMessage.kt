package com.unikit.phone.model

import org.json.JSONObject

/**
 * Mirrors the server's GlassState exactly (see
 * unikit/docs/data_contracts.md#glassstate). Ported verbatim from
 * glass-app/model/GlassStateMessage.kt -- Phone must not invent its own
 * competing state shape, same rule as Glass.
 */
data class GlassStateMessage(
    val connected: Boolean,
    val patientId: String?,
    val examId: String?,
    val patientDisplayName: String?,
    val age: Int?,
    val sex: String?,
    val bpSys: Double?,
    val bpDia: Double?,
    val spo2: Double?,
    val heartRate: Double?,
    val temperature: Double?,
    val examMode: String,
    val laterality: String,
    val recording: Boolean,
    val frozen: Boolean,
    val endoscopeConnected: Boolean,
    val lastSeen: String?,
    val simulated: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject): GlassStateMessage = GlassStateMessage(
            connected = json.optBoolean("connected", false),
            patientId = json.optNullableString("patientId"),
            examId = json.optNullableString("examId"),
            patientDisplayName = json.optNullableString("patientDisplayName"),
            age = json.optNullableInt("age"),
            sex = json.optNullableString("sex"),
            bpSys = json.optNullableDouble("bpSys"),
            bpDia = json.optNullableDouble("bpDia"),
            spo2 = json.optNullableDouble("spo2"),
            heartRate = json.optNullableDouble("heartRate"),
            temperature = json.optNullableDouble("temperature"),
            examMode = json.optString("examMode", "NONE"),
            laterality = json.optString("laterality", "NONE"),
            recording = json.optBoolean("recording", false),
            frozen = json.optBoolean("frozen", false),
            endoscopeConnected = json.optBoolean("endoscopeConnected", false),
            lastSeen = json.optNullableString("lastSeen"),
            simulated = json.optBoolean("simulated", true),
        )
    }
}
