package com.unikit.phone.model

import org.json.JSONObject

/** Mirrors app/models/exam.py::ExamOut. */
data class Exam(
    val examId: String,
    val patientId: String,
    val startedAt: String?,
    val endedAt: String?,
    val status: String,
) {
    companion object {
        fun fromJson(json: JSONObject): Exam = Exam(
            examId = json.optString("examId"),
            patientId = json.optString("patientId"),
            startedAt = json.optNullableString("startedAt"),
            endedAt = json.optNullableString("endedAt"),
            status = json.optString("status"),
        )
    }
}
