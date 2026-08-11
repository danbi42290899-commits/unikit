package com.unikit.phone.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.unikit.phone.state.UniHubConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Shared reconnect/backoff machinery for a single WebSocket endpoint.
 * Ported verbatim from glass-app's ReconnectingWebSocketClient --
 * TelemetryClient and ControlClient both extend this rather than each
 * re-implementing exponential backoff.
 *
 * Survives temporary Wi-Fi loss without the app restarting: on any
 * failure/close it schedules another attempt with bounded exponential
 * backoff (1s, 2s, 4s, ... capped at 30s) and keeps retrying forever.
 */
abstract class ReconnectingWebSocketClient(
    private val httpClient: OkHttpClient,
    private val url: String,
    protected val logTag: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var retryAttempt = 0
    private var stopped = true

    private val _connectionState = MutableStateFlow(UniHubConnectionState.CONNECTING)
    val connectionState: StateFlow<UniHubConnectionState> = _connectionState.asStateFlow()

    /** Called on the main thread for every text frame received. */
    protected abstract fun onMessageReceived(text: String)

    /** Called on the main thread for every binary frame received. No-op unless overridden. */
    protected open fun onBinaryMessageReceived(bytes: ByteString) {}

    /** Called on the main thread right after the socket opens. */
    protected open fun onConnected() {}

    fun start() {
        if (!stopped) return
        stopped = false
        retryAttempt = 0
        _connectionState.value = UniHubConnectionState.CONNECTING
        connect()
    }

    fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "client stop")
        webSocket = null
    }

    /** Returns false if there is currently no open socket to send on. */
    fun send(text: String): Boolean {
        val socket = webSocket ?: return false
        return socket.send(text)
    }

    private fun connect() {
        if (stopped) return
        Log.i(logTag, "connecting to $url")
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(logTag, "connected")
            mainHandler.post {
                retryAttempt = 0
                _connectionState.value = UniHubConnectionState.CONNECTED
                onConnected()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            mainHandler.post { onMessageReceived(text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            mainHandler.post { onBinaryMessageReceived(bytes) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(logTag, "closed: code=$code reason=$reason")
            mainHandler.post { scheduleReconnect() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(logTag, "connection failure: ${t.message}")
            mainHandler.post { scheduleReconnect() }
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        webSocket = null
        _connectionState.value = UniHubConnectionState.DISCONNECTED
        val delayMs = backoffDelayMillis(retryAttempt)
        retryAttempt++
        Log.i(logTag, "reconnecting in ${delayMs}ms (attempt $retryAttempt)")
        mainHandler.postDelayed({
            if (!stopped) {
                _connectionState.value = UniHubConnectionState.RECONNECTING
                connect()
            }
        }, delayMs)
    }

    companion object {
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
