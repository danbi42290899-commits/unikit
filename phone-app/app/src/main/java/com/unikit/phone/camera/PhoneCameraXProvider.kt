package com.unikit.phone.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.unikit.phone.network.CameraUploadClient
import com.unikit.phone.ui.toJpegBytes
import java.util.concurrent.ExecutorService

/**
 * The phone's own back camera as the current CameraFeedProvider
 * implementation. This is today's MainActivity CameraX bind/preview/
 * upload logic (Phase 3), extracted verbatim into its own class per
 * ~/.claude/plans/curious-floating-flame.md #5 so OtoscopeFragment and
 * ColposcopeFragment can each own an instance.
 *
 * Unlike the old MainActivity (which had a manual START/STOP STREAMING
 * toggle), Otoscope/Colposcope screens always relay while visible --
 * there's no separate "streaming" concept in the exam workflow, Glass
 * should always see what the otoscope/colposcope camera sees while that
 * screen is open.
 */
class PhoneCameraXProvider(
    private val context: Context,
    private val cameraExecutor: ExecutorService,
    private val uploadClient: CameraUploadClient,
) : CameraFeedProvider {

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastFrameSentAtMs = 0L

    override fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        uploadClient.start()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::onFrameAvailable) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        uploadClient.stop()
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
    }

    private fun onFrameAvailable(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameSentAtMs < MIN_FRAME_INTERVAL_MS) return
            lastFrameSentAtMs = now

            val jpegBytes = imageProxy.toJpegBytes(JPEG_QUALITY) ?: return
            uploadClient.sendFrame(jpegBytes)
        } catch (e: Exception) {
            Log.w(TAG, "frame relay failed: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private const val TAG = "PhoneCameraXProvider"
        private const val JPEG_QUALITY = 60

        // Throttles to ~6 fps -- see original MainActivity rationale:
        // faster buys little for a HUD-sized display and just burns
        // Wi-Fi/battery relaying frames Glass can't usefully show sooner.
        private const val MIN_FRAME_INTERVAL_MS = 160L
    }
}
