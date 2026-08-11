package com.unikit.phone.model

import org.json.JSONObject

/**
 * Deep-parsed `event` telemetry message (see app/models/vitals.py::
 * EventMessage). There is no GET /events REST endpoint, so this is only
 * ever populated from commands observed live over /ws/telemetry while the
 * Phone app is connected -- same "no backlog on reconnect" limitation
 * documented in docs/websocket_spec.md. UniHubRepository keeps a bounded
 * in-memory list of these for EcgFragment/ReportFragment to read.
 */
data class EventLogEntry(
    val eventType: String,
    val timestamp: String?,
    val examId: String?,
    val detail: String,
) {
    companion object {
        fun fromJson(json: JSONObject): EventLogEntry = EventLogEntry(
            eventType = json.optString("eventType"),
            timestamp = json.optNullableString("timestamp"),
            examId = json.optNullableString("examId"),
            detail = json.optJSONObject("detail")?.toString() ?: "{}",
        )
    }
}
