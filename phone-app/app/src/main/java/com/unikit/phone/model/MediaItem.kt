package com.unikit.phone.model

import org.json.JSONObject

/** Mirrors app/models/media.py::MediaOut. */
data class MediaItem(
    val mediaId: String,
    val examId: String,
    val patientId: String?,
    val mode: String, // OTOSCOPE | COLPOSCOPE
    val laterality: String, // LEFT | RIGHT | NONE
    val sourceDevice: String,
    val timestamp: String?,
    val fileUrl: String, // relative, e.g. "/media/{mediaId}/file"
) {
    companion object {
        fun fromJson(json: JSONObject): MediaItem = MediaItem(
            mediaId = json.optString("mediaId"),
            examId = json.optString("examId"),
            patientId = json.optNullableString("patientId"),
            mode = json.optString("mode"),
            laterality = json.optString("laterality", "NONE"),
            sourceDevice = json.optString("sourceDevice"),
            timestamp = json.optNullableString("timestamp"),
            fileUrl = json.optString("fileUrl"),
        )
    }
}
