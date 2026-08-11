package com.unikit.phone.model

import org.json.JSONObject

/**
 * Deep-parsed `raw_signal` telemetry message (see
 * app/models/vitals.py::RawSignalMessage). Used by EcgFragment (signal ==
 * "ECG") and HeartRateFragment/Spo2TemperatureFragment (signal ==
 * "PPG_IR" / "PPG_RED") to drive the simple Canvas waveform plot.
 */
data class RawSignalSnapshot(
    val signal: String,
    val sampleRate: Double?,
    val unit: String,
    val quality: String,
    val simulated: Boolean,
    val timestamp: String?,
    val samples: List<Double>,
) {
    companion object {
        fun fromJson(json: JSONObject): RawSignalSnapshot {
            val samplesArray = json.optJSONArray("samples")
            val samples = ArrayList<Double>(samplesArray?.length() ?: 0)
            if (samplesArray != null) {
                for (i in 0 until samplesArray.length()) {
                    samples.add(samplesArray.optDouble(i))
                }
            }
            return RawSignalSnapshot(
                signal = json.optString("signal"),
                sampleRate = json.optNullableDouble("sampleRate"),
                unit = json.optString("unit"),
                quality = json.optString("quality", "unknown"),
                simulated = json.optBoolean("simulated", true),
                timestamp = json.optNullableString("timestamp"),
                samples = samples,
            )
        }
    }
}
