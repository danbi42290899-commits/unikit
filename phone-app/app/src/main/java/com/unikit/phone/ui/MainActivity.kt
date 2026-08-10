package com.unikit.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.unikit.phone.R
import com.unikit.phone.network.CameraUploadClient
import com.unikit.phone.network.UniHubConfig
import com.unikit.phone.network.UploadConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Phase 3 (minimal): captures the back camera and streams JPEG frames to
 * UNI-HUB's /ws/camera relay, which fans them out to any connected Glass.
 * Preview always runs so the person holding the phone can see what's being
 * sent; the START/STOP button controls only the upload, not the camera.
 */
class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var cameraPreview: PreviewView
    private lateinit var textStatus: TextView
    private lateinit var buttonStreamToggle: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var uploadClient: CameraUploadClient
    private var camera: Camera? = null
    private var streaming = false
    private var lastFrameSentAtMs = 0L

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                textStatus.text = "CAMERA PERMISSION DENIED"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraPreview = findViewById(R.id.cameraPreview)
        textStatus = findViewById(R.id.textStatus)
        buttonStreamToggle = findViewById(R.id.buttonStreamToggle)

        applyConfigOverridesFromIntent()

        cameraExecutor = Executors.newSingleThreadExecutor()
        val okHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        uploadClient = CameraUploadClient(okHttpClient)

        buttonStreamToggle.setOnClickListener { toggleStreaming() }
        observeUploadState()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        Log.i(TAG, "UNI-KIT Phone started; camera upload target=${UniHubConfig.cameraUploadUrl}")
    }

    override fun onDestroy() {
        uploadClient.stop()
        activityScope.cancel()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    /**
     * Same override mechanism as glass-app's GlassHudActivity, so both
     * apps can be repointed at a UNI-HUB without a rebuild:
     * adb shell am start -n com.unikit.phone/.ui.MainActivity \
     *     --es uni_hub_host 192.168.0.42 --ei uni_hub_port 8000
     */
    private fun applyConfigOverridesFromIntent() {
        intent?.getStringExtra(EXTRA_HOST)?.let {
            Log.i(TAG, "UNI_HUB_HOST overridden via intent extra: $it")
            UniHubConfig.host = it
        }
        if (intent?.hasExtra(EXTRA_PORT) == true) {
            val port = intent.getIntExtra(EXTRA_PORT, UniHubConfig.port)
            Log.i(TAG, "UNI_HUB_PORT overridden via intent extra: $port")
            UniHubConfig.port = port
        }
    }

    private fun toggleStreaming() {
        streaming = !streaming
        if (streaming) {
            uploadClient.start()
            buttonStreamToggle.text = getString(R.string.button_stop)
        } else {
            uploadClient.stop()
            buttonStreamToggle.text = getString(R.string.button_start)
        }
    }

    private fun observeUploadState() {
        activityScope.launch {
            uploadClient.connectionState.collect { state ->
                val (label, colorRes) = when (state) {
                    UploadConnectionState.CONNECTED ->
                        R.string.status_connected to R.color.status_connected
                    UploadConnectionState.CONNECTING, UploadConnectionState.RECONNECTING ->
                        R.string.status_connecting to R.color.status_connecting
                    UploadConnectionState.DISCONNECTED ->
                        R.string.status_disconnected to R.color.status_disconnected
                }
                textStatus.text = getString(label)
                textStatus.setTextColor(ContextCompat.getColor(this@MainActivity, colorRes))
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::onFrameAvailable) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFrameAvailable(imageProxy: ImageProxy) {
        try {
            if (!streaming) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameSentAtMs < MIN_FRAME_INTERVAL_MS) return
            lastFrameSentAtMs = now

            val jpegBytes = imageProxy.toJpegBytes(JPEG_QUALITY) ?: return
            uploadClient.sendFrame(jpegBytes)
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_HOST = "uni_hub_host"
        private const val EXTRA_PORT = "uni_hub_port"
        private const val JPEG_QUALITY = 60

        // Throttles to ~6 fps. Faster buys little for a HUD-sized display
        // and just burns Wi-Fi/battery relaying frames Glass can't usefully
        // show any sooner.
        private const val MIN_FRAME_INTERVAL_MS = 160L
    }
}
