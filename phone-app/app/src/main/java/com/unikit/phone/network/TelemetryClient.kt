package com.unikit.phone.network

import android.util.Log
import com.unikit.phone.model.DeviceStatusInfo
import com.unikit.phone.model.EventLogEntry
import com.unikit.phone.model.GlassStateMessage
import com.unikit.phone.model.RawSignalSnapshot
import com.unikit.phone.model.TelemetryMessage
import com.unikit.phone.model.TelemetryMessageParser
import com.unikit.phone.model.VitalsSnapshot
import okhttp3.OkHttpClient

/**
 * Read-only subscription to /ws/telemetry. Broadcast channel -- Phone
 * never sends anything meaningful on it (see docs/websocket_spec.md).
 *
 * Ported from glass-app/network/TelemetryClient.kt and extended per the
 * build plan (#5): Glass only deep-parsed `glass_state`; Phone also needs
 * `vitals` (trend history), `device_status` (Device Status screen), and
 * `raw_signal`/`event` (waveforms + report event log), so this adds one
 * callback per new message type, following the exact same pattern as the
 * original onGlassState/onAnyMessage.
 *
 * Connected without `?client=GLASS` -- Phone's presence isn't tracked in
 * the device registry the way Glass's is (there's no PHONE device_status
 * entry to flip; Phone's own device status is PHONE_CAMERA, tracked via
 * /ws/camera instead).
 */
class TelemetryClient(
    httpClient: OkHttpClient,
    private val onGlassState: (GlassStateMessage) -> Unit,
    private val onVitals: (VitalsSnapshot) -> Unit,
    private val onDeviceStatus: (DeviceStatusInfo) -> Unit,
    private val onRawSignal: (RawSignalSnapshot) -> Unit,
    private val onEvent: (EventLogEntry) -> Unit,
    private val onAnyMessage: () -> Unit,
) : ReconnectingWebSocketClient(httpClient, UniHubConfig.telemetryUrl, TAG) {

    override fun onMessageReceived(text: String) {
        onAnyMessage()
        when (val message = TelemetryMessageParser.parse(text) ?: return) {
            is TelemetryMessage.GlassState -> {
                Log.d(TAG, "glass_state received (examId=${message.state.examId})")
                onGlassState(message.state)
            }
            is TelemetryMessage.Vitals -> onVitals(message.vitals)
            is TelemetryMessage.DeviceStatus -> onDeviceStatus(message.status)
            is TelemetryMessage.RawSignal -> onRawSignal(message.signal)
            is TelemetryMessage.Event -> onEvent(message.event)
            is TelemetryMessage.Other -> {
                // Nothing else to route in this pass.
            }
        }
    }

    companion object {
        private const val TAG = "TelemetryClient"
    }
}
