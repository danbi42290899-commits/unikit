package com.unikit.glass.repository

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.unikit.glass.model.GlassStateMessage
import com.unikit.glass.network.CameraClient
import com.unikit.glass.network.ControlClient
import com.unikit.glass.network.TelemetryClient
import com.unikit.glass.state.UniHubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Single point where the UI reads UNI-HUB state from and sends commands
 * through. Owns both WebSocket clients; the UI never touches
 * TelemetryClient/ControlClient directly.
 */
class UniHubRepository(
    okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var lastTelemetryReceivedAtMs: Long = 0L

    private val _glassState = MutableStateFlow<GlassStateMessage?>(null)
    val glassState: StateFlow<GlassStateMessage?> = _glassState.asStateFlow()

    private val _cameraFrame = MutableStateFlow<Bitmap?>(null)
    val cameraFrame: StateFlow<Bitmap?> = _cameraFrame.asStateFlow()

    /** true = last CAPTURE was acknowledged ok, false = it failed/errored. */
    private val _captureResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val captureResults: SharedFlow<Boolean> = _captureResults.asSharedFlow()

    private val telemetryClient = TelemetryClient(
        httpClient = okHttpClient,
        onGlassState = { _glassState.value = it },
        onAnyMessage = { lastTelemetryReceivedAtMs = SystemClock.elapsedRealtime() },
    )

    private val controlClient = ControlClient(
        httpClient = okHttpClient,
        onAck = { command, ok -> if (command == "CAPTURE") _captureResults.tryEmit(ok) },
        onError = { message ->
            Log.w(TAG, "control error while awaiting ack: $message")
            _captureResults.tryEmit(false)
        },
    )

    private val cameraClient = CameraClient(
        httpClient = okHttpClient,
        decodeScope = scope,
        onFrame = { _cameraFrame.value = it },
    )

    /**
     * true once more than STALE_THRESHOLD_MS has passed since the last
     * message of ANY type arrived on /ws/telemetry (or none has ever
     * arrived). This is what prevents Glass from showing frozen stale
     * vitals as current if the link silently dies without the TCP socket
     * noticing right away.
     */
    private val _linkStale = MutableStateFlow(true)

    /**
     * What the HUD should actually render as UNI-HUB's connection state:
     * the raw socket lifecycle, overridden to DISCONNECTED if the socket
     * claims CONNECTED but nothing has actually arrived recently.
     */
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
        cameraClient.start()
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
        cameraClient.stop()
    }

    /** Returns false immediately (no ack will follow) if not connected. */
    fun sendCapture(): Boolean = controlClient.sendCommand("CAPTURE")

    /**
     * Generic passthrough for the other /ws/control commands (FREEZE/
     * UNFREEZE, START_RECORDING/STOP_RECORDING) that double-tap/long-press
     * now send. CAPTURE keeps using sendCapture() above since it's the only
     * command whose ack is surfaced back through captureResults.
     */
    fun sendCommand(command: String): Boolean = controlClient.sendCommand(command)

    companion object {
        private const val TAG = "UniHubRepository"

        // Matches the server's DEVICE_STALE_TIMEOUT_SEC (app/config.py) so
        // Glass and UNI-HUB agree on what "stale" means.
        private const val STALE_THRESHOLD_MS = 5_000L
        private const val STALE_CHECK_INTERVAL_MS = 1_000L
    }
}
