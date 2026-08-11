package com.unikit.phone.network

import com.unikit.phone.model.DeviceStatusInfo
import com.unikit.phone.model.Exam
import com.unikit.phone.model.MediaItem
import com.unikit.phone.model.Patient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Thrown for any non-2xx REST response, with the server's `detail` (if any) folded in. */
class UniHubApiException(message: String) : IOException(message)

/**
 * Raw OkHttp + coroutines REST client for the Mock UNI-HUB (see
 * docs/api_spec.md). No Retrofit -- see the gradle offline-cache
 * constraint in ~/.claude/plans/curious-floating-flame.md #1. Every call
 * runs synchronously on Dispatchers.IO inside a suspend function, which
 * the plan explicitly allows as an alternative to
 * suspendCancellableCoroutine for this app's simple request/response
 * (non-streaming) REST calls.
 */
class UniHubApiClient(private val httpClient: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createPatient(patientId: String, displayName: String, age: Int?, sex: String?): Patient =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("patientId", patientId)
                put("displayName", displayName)
                put("age", age ?: JSONObject.NULL)
                put("sex", sex ?: JSONObject.NULL)
            }
            Patient.fromJson(postJson("${UniHubConfig.baseHttpUrl}/patients", body))
        }

    suspend fun getPatient(patientId: String): Patient = withContext(Dispatchers.IO) {
        Patient.fromJson(getJsonObject("${UniHubConfig.baseHttpUrl}/patients/$patientId"))
    }

    suspend fun createExam(patientId: String): Exam = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("patientId", patientId) }
        Exam.fromJson(postJson("${UniHubConfig.baseHttpUrl}/exams", body))
    }

    suspend fun getExam(examId: String): Exam = withContext(Dispatchers.IO) {
        Exam.fromJson(getJsonObject("${UniHubConfig.baseHttpUrl}/exams/$examId"))
    }

    suspend fun endExam(examId: String): Exam = withContext(Dispatchers.IO) {
        Exam.fromJson(postJson("${UniHubConfig.baseHttpUrl}/exams/$examId/end", body = null))
    }

    suspend fun getDevices(): List<DeviceStatusInfo> = withContext(Dispatchers.IO) {
        val array = getJsonArray("${UniHubConfig.baseHttpUrl}/devices")
        (0 until array.length()).map { i -> DeviceStatusInfo.fromJson(array.getJSONObject(i)) }
    }

    suspend fun getExamMedia(examId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val array = getJsonArray("${UniHubConfig.baseHttpUrl}/exams/$examId/media")
        (0 until array.length()).map { i -> MediaItem.fromJson(array.getJSONObject(i)) }
    }

    /** Fetches raw bytes from an absolute or fileUrl-relative path (e.g. a MediaItem.fileUrl). */
    suspend fun fetchBytes(urlOrPath: String): ByteArray = withContext(Dispatchers.IO) {
        val url = if (urlOrPath.startsWith("http")) urlOrPath else "${UniHubConfig.baseHttpUrl}$urlOrPath"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UniHubApiException("HTTP ${response.code} fetching $url")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    // ---------------------------------------------------------------
    // Low-level helpers
    // ---------------------------------------------------------------

    private fun postJson(url: String, body: JSONObject?): JSONObject {
        val requestBody = (body ?: JSONObject()).toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(requestBody).build()
        return JSONObject(execute(request))
    }

    private fun getJsonObject(url: String): JSONObject =
        JSONObject(execute(Request.Builder().url(url).get().build()))

    private fun getJsonArray(url: String): JSONArray =
        JSONArray(execute(Request.Builder().url(url).get().build()))

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(bodyStr).opt("detail")?.toString() ?: bodyStr
                } catch (e: Exception) {
                    bodyStr
                }
                throw UniHubApiException("HTTP ${response.code}: $detail")
            }
            return bodyStr
        }
    }
}
