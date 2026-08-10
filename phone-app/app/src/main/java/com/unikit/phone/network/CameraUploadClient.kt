package com.unikit.phone.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer

enum class UploadConnectionState { CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED }

/**
 * Sends camera frames to /ws/camera?role=PHONE, one binary WebSocket
 * message per JPEG frame. Mirrors the bounded exponential backoff
 * reconnect glass-app's ReconnectingWebSocketClient uses, so a transient
 * Wi-Fi drop during a demo doesn't require restarting the app.
 */
class CameraUploadClient(private val httpClient: OkHttpClient) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var retryAttempt = 0
    private var stopped = true

    private val _connectionState = MutableStateFlow(UploadConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UploadConnectionState> = _connectionState.asStateFlow()

    fun start() {
        if (!stopped) return
        stopped = false
        retryAttempt = 0
        _connectionState.value = UploadConnectionState.CONNECTING
        connect()
    }

    fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "client stop")
        webSocket = null
        _connectionState.value = UploadConnectionState.DISCONNECTED
    }

    /** Returns false immediately (frame dropped) if not currently connected. */
    fun sendFrame(jpegBytes: ByteArray): Boolean {
        val socket = webSocket ?: return false
        return socket.send(Buffer().write(jpegBytes).readByteString())
    }

    private fun connect() {
        if (stopped) return
        Log.i(TAG, "connecting to ${UniHubConfig.cameraUploadUrl}")
        val request = Request.Builder().url(UniHubConfig.cameraUploadUrl).build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "connected")
            mainHandler.post {
                retryAttempt = 0
                _connectionState.value = UploadConnectionState.CONNECTED
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "closed: code=$code reason=$reason")
            mainHandler.post { scheduleReconnect() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "connection failure: ${t.message}")
            mainHandler.post { scheduleReconnect() }
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        webSocket = null
        _connectionState.value = UploadConnectionState.DISCONNECTED
        val delayMs = backoffDelayMillis(retryAttempt)
        retryAttempt++
        Log.i(TAG, "reconnecting in ${delayMs}ms (attempt $retryAttempt)")
        mainHandler.postDelayed({
            if (!stopped) {
                _connectionState.value = UploadConnectionState.RECONNECTING
                connect()
            }
        }, delayMs)
    }

    companion object {
        private const val TAG = "CameraUploadClient"
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30_000L
        private const val MAX_EXPONENT = 5 // 1000ms * 2^5 = 32s, then clamped to 30s

        fun backoffDelayMillis(attempt: Int): Long {
            val exponent = attempt.coerceAtMost(MAX_EXPONENT)
            val delay = BASE_DELAY_MS * (1L shl exponent)
            return delay.coerceAtMost(MAX_DELAY_MS)
        }
    }
}
