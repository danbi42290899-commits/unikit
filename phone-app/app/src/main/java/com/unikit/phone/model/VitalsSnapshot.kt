package com.unikit.phone.model

import org.json.JSONObject

/**
 * Deep-parsed `vitals` telemetry message (see docs/data_contracts.md).
 * New for Phone -- Glass never needed this shape because it only reads
 * vitals via `glass_state`, but Phone's Heart Rate / SpO2+Temp / Blood
 * Pressure screens need the envelope fields (quality/timestamp) too.
 */
data class VitalsSnapshot(
    val bpSys: Double?,
    val bpDia: Double?,
    val spo2: Double?,
    val heartRate: Double?,
    val temperature: Double?,
    val quality: String,
    val simulated: Boolean,
    val timestamp: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): VitalsSnapshot {
            val data = json.optJSONObject("data") ?: JSONObject()
            return VitalsSnapshot(
                bpSys = data.optNullableDouble("bpSys"),
                bpDia = data.optNullableDouble("bpDia"),
                spo2 = data.optNullableDouble("spo2"),
                heartRate = data.optNullableDouble("heartRate"),
                temperature = data.optNullableDouble("temperature"),
                quality = json.optString("quality", "unknown"),
                simulated = json.optBoolean("simulated", true),
                timestamp = json.optNullableString("timestamp"),
            )
        }
    }
}
