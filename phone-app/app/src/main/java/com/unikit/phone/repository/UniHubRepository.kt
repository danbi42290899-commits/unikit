package com.unikit.phone.repository

import android.os.SystemClock
import android.util.Log
import com.unikit.phone.model.DeviceStatusInfo
import com.unikit.phone.model.EventLogEntry
import com.unikit.phone.model.Exam
import com.unikit.phone.model.GlassStateMessage
import com.unikit.phone.model.MediaItem
import com.unikit.phone.model.Patient
import com.unikit.phone.model.RawSignalSnapshot
import com.unikit.phone.model.VitalsSnapshot
import com.unikit.phone.network.ControlClient
import com.unikit.phone.network.TelemetryClient
import com.unikit.phone.network.UniHubApiClient
import com.unikit.phone.state.UniHubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Result of a /ws/control command_ack, with the CAPTURE-specific mediaId/reason fields intact. */
data class CommandAck(val command: String, val ok: Boolean, val detail: Map<String, String>)

/**
 * Single point where the UI reads UNI-HUB state from and sends commands
 * through. Owns both WebSocket clients plus the REST client; the UI never
 * touches TelemetryClient/ControlClient/UniHubApiClient directly.
 *
 * Ported from glass-app/repository/UniHubRepository.kt and extended per
 * ~/.claude/plans/curious-floating-flame.md #5:
 *  - generic sendCommand() (Glass already had this)
 *  - vitals / vitalsHistory / deviceStatus / rawSignal / events StateFlows
 *    fed by TelemetryClient's new parsing
 *  - thin suspend wrappers around UniHubApiClient for patient/exam/media
 *  - no CameraClient/cameraFrame -- Phone is the camera *source* (local
 *    CameraX preview via PhoneCameraXProvider), not a relay viewer.
 */
class UniHubRepository(
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val apiClient = UniHubApiClient(okHttpClient)

    @Volatile
    private var lastTelemetryReceivedAtMs: Long = 0L

    private val _glassState = MutableStateFlow<GlassStateMessage?>(null)
    val glassState: StateFlow<GlassStateMessage?> = _glassState.asStateFlow()

    private val _vitals = MutableStateFlow<VitalsSnapshot?>(null)
    val vitals: StateFlow<VitalsSnapshot?> = _vitals.asStateFlow()

    /** Client-side rolling buffer since exam start -- see plan §8, no new server storage. */
    private val _vitalsHistory = MutableStateFlow<List<VitalsSnapshot>>(emptyList())
    val vitalsHistory: StateFlow<List<VitalsSnapshot>> = _vitalsHistory.asStateFlow()

    private val _deviceStatus = MutableStateFlow<Map<String, DeviceStatusInfo>>(emptyMap())
    val deviceStatus: StateFlow<Map<String, DeviceStatusInfo>> = _deviceStatus.asStateFlow()

    /** Every raw_signal chunk (ECG + both PPG channels) -- fragments filter by `.signal`. */
    private val _rawSignal = MutableSharedFlow<RawSignalSnapshot>(extraBufferCapacity = 16)
    val rawSignal: SharedFlow<RawSignalSnapshot> = _rawSignal.asSharedFlow()

    /** Bounded event log, live-observed only (no GET /events endpoint exists). */
    private val _events = MutableStateFlow<List<EventLogEntry>>(emptyList())
    val events: StateFlow<List<EventLogEntry>> = _events.asStateFlow()

    private val _commandAcks = MutableSharedFlow<CommandAck>(extraBufferCapacity = 8)
    val commandAcks: SharedFlow<CommandAck> = _commandAcks.asSharedFlow()

    private val telemetryClient = TelemetryClient(
        httpClient = okHttpClient,
        onGlassState = { _glassState.value = it },
        onVitals = { v ->
            _vitals.value = v
            _vitalsHistory.value = (_vitalsHistory.value + v).takeLast(VITALS_HISTORY_LIMIT)
        },
        onDeviceStatus = { d -> _deviceStatus.value = _deviceStatus.value + (d.device to d) },
        onRawSignal = { s -> _rawSignal.tryEmit(s) },
        onEvent = { e -> _events.value = (_events.value + e).takeLast(EVENT_HISTORY_LIMIT) },
        onAnyMessage = { lastTelemetryReceivedAtMs = SystemClock.elapsedRealtime() },
    )

    private val controlClient = ControlClient(
        httpClient = okHttpClient,
        onAck = { command, ok, detail -> _commandAcks.tryEmit(CommandAck(command, ok, detail)) },
        onError = { message ->
            Log.w(TAG, "control error: $message")
            _commandAcks.tryEmit(CommandAck("ERROR", false, mapOf("reason" to message)))
        },
    )

    /**
     * true once more than STALE_THRESHOLD_MS has passed since the last
     * message of ANY type arrived on /ws/telemetry (or none has ever
     * arrived) -- prevents Phone from showing frozen stale vitals as
     * current if the link silently dies. Same convention as Glass's
     * renderGlassState(null) on staleness.
     */
    private val _linkStale = MutableStateFlow(true)

    val connectionState: StateFlow<UniHubConnectionState> =
        combine(telemetryClient.connectionState, _linkStale) { raw, stale ->
            if (stale && raw == UniHubConnectionState.CONNECTED) {
                UniHubConnectionState.DISCONNECTED
            } else {
                raw
            }
        }.stateIn(scope, SharingStarted.Eagerly, UniHubConnectionState.CONNECTING)

    fun start() {
        Log.i(TAG, "starting UNI-HUB repository")
        telemetryClient.start()
        controlClient.start()
        scope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - lastTelemetryReceivedAtMs
                _linkStale.value = lastTelemetryReceivedAtMs == 0L || elapsed > STALE_THRESHOLD_MS
                delay(STALE_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        telemetryClient.stop()
        controlClient.stop()
    }

    /** Clears the client-side rolling buffers -- call on new exam start / END EXAMINATION. */
    fun resetSessionBuffers() {
        _vitalsHistory.value = emptyList()
        _events.value = emptyList()
    }

    /** Returns false immediately (no ack will follow) if the control socket isn't connected. */
    fun sendCommand(command: String): Boolean = controlClient.sendCommand(command)

    // ---------------------------------------------------------------
    // REST pass-throughs (see docs/api_spec.md)
    // ---------------------------------------------------------------

    suspend fun createPatient(patientId: String, displayName: String, age: Int?, sex: String?): Patient =
        apiClient.createPatient(patientId, displayName, age, sex)

    suspend fun getPatient(patientId: String): Patient = apiClient.getPatient(patientId)

    suspend fun createExam(patientId: String): Exam = apiClient.createExam(patientId)

    suspend fun getExam(examId: String): Exam = apiClient.getExam(examId)

    suspend fun endExam(examId: String): Exam = apiClient.endExam(examId)

    suspend fun getDevicesSnapshot(): List<DeviceStatusInfo> = apiClient.getDevices()

    suspend fun getExamMedia(examId: String): List<MediaItem> = apiClient.getExamMedia(examId)

    suspend fun fetchMediaBytes(fileUrl: String): ByteArray = apiClient.fetchBytes(fileUrl)

    companion object {
        private const val TAG = "UniHubRepository"

        // Matches the server's DEVICE_STALE_TIMEOUT_SEC (app/config.py) so
        // Phone and UNI-HUB agree on what "stale" means.
        private const val STALE_THRESHOLD_MS = 5_000L
        private const val STALE_CHECK_INTERVAL_MS = 1_000L

        private const val VITALS_HISTORY_LIMIT = 300
        private const val EVENT_HISTORY_LIMIT = 100
    }
}
