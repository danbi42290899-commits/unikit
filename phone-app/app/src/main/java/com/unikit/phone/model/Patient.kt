package com.unikit.phone.model

import org.json.JSONObject

/** Mirrors app/models/patient.py::PatientOut. */
data class Patient(
    val patientId: String,
    val displayName: String,
    val age: Int?,
    val sex: String?,
    val createdAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): Patient = Patient(
            patientId = json.optString("patientId"),
            displayName = json.optString("displayName"),
            age = json.optNullableInt("age"),
            sex = json.optNullableString("sex"),
            createdAt = json.optNullableString("createdAt"),
        )
    }
}
