package com.unikit.glass.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.ByteString

/**
 * Read-only subscription to /ws/camera?role=GLASS. Frames arrive as raw
 * JPEG bytes on the WebSocket's binary channel (see server/app/ws/camera.py).
 * Decoding happens off the main thread because this is a continuous stream,
 * not an occasional message like /ws/control acks -- decoding on the UI
 * thread would jank the HUD once frames arrive at more than a couple fps.
 */
class CameraClient(
    httpClient: OkHttpClient,
    private val decodeScope: CoroutineScope,
    private val onFrame: (Bitmap) -> Unit,
) : ReconnectingWebSocketClient(httpClient, UniHubConfig.cameraUrl, TAG) {

    override fun onMessageReceived(text: String) {
        // Glass never receives text on this channel; nothing to do.
    }

    override fun onBinaryMessageReceived(bytes: ByteString) {
        val raw = bytes.toByteArray()
        decodeScope.launch(Dispatchers.Default) {
            val bitmap = try {
                BitmapFactory.decodeByteArray(raw, 0, raw.size)
            } catch (t: Throwable) {
                Log.w(TAG, "failed to decode camera frame: ${t.message}")
                null
            }
            if (bitmap != null) {
                onFrame(bitmap)
            }
        }
    }

    companion object {
        private const val TAG = "CameraClient"
    }
}
