package com.unikit.glass.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.unikit.glass.R
import com.unikit.glass.input.GlassGestureListener
import com.unikit.glass.model.GlassStateMessage
import com.unikit.glass.network.UniHubConfig
import com.unikit.glass.repository.UniHubRepository
import com.unikit.glass.state.UniHubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Phase 2 UNI-KIT Glass HUD. Single-screen, single-Activity app: connects
 * to Mock UNI-HUB, renders the shared GlassState, and forwards touchpad
 * TAP as a CAPTURE command. See docs/glass_app.md for the full picture.
 */
class GlassHudActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: UniHubRepository
    private lateinit var gestureDetector: GestureDetector
    private val bannerHandler = Handler(Looper.getMainLooper())

    private var recordingStartedAtElapsedMs: Long? = null
    private var lastRenderedConnectionState: UniHubConnectionState? = null
    private var persistentDisconnectedBannerShown = false

    private lateinit var textBp: TextView
    private lateinit var textSpo2: TextView
    private lateinit var textHr: TextView
    private lateinit var textTemp: TextView
    private lateinit var textPatient: TextView
    private lateinit var textConnection: TextView
    private lateinit var textSimBadge: TextView
    private lateinit var textExamMode: TextView
    private lateinit var textRecording: TextView
    private lateinit var textBanner: TextView
    private lateinit var imageCamera: ImageView
    private lateinit var textEndoscopePlaceholder: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_glass_hud)
        bindViews()
        applyConfigOverridesFromIntent()

        val okHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        repository = UniHubRepository(okHttpClient, activityScope)

        gestureDetector = GestureDetector(this, GlassGestureListener(onTap = ::onCaptureTap))
        findViewById<View>(R.id.root).setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        observeRepository()
        repository.start()
        Log.i(TAG, "UNI-KIT Glass HUD started; telemetry=${UniHubConfig.telemetryUrl} control=${UniHubConfig.controlUrl}")
    }

    override fun onDestroy() {
        repository.stop()
        activityScope.cancel()
        bannerHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun bindViews() {
        textBp = findViewById(R.id.textBp)
        textSpo2 = findViewById(R.id.textSpo2)
        textHr = findViewById(R.id.textHr)
        textTemp = findViewById(R.id.textTemp)
        textPatient = findViewById(R.id.textPatient)
        textConnection = findViewById(R.id.textConnection)
        textSimBadge = findViewById(R.id.textSimBadge)
        textExamMode = findViewById(R.id.textExamMode)
        textRecording = findViewById(R.id.textRecording)
        textBanner = findViewById(R.id.textBanner)
        imageCamera = findViewById(R.id.imageCamera)
        textEndoscopePlaceholder = findViewById(R.id.textEndoscopePlaceholder)
    }

    /**
     * Same-session config override without a rebuild, e.g.:
     * adb shell am start -n com.unikit.glass/.ui.GlassHudActivity \
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

    private fun observeRepository() {
        activityScope.launch { repository.glassState.collect { state -> renderGlassState(state) } }
        activityScope.launch { repository.connectionState.collect { state -> renderConnectionState(state) } }
        activityScope.launch { repository.cameraFrame.collect { frame -> renderCameraFrame(frame) } }
        activityScope.launch {
            repository.captureResults.collect { ok ->
                showTransientBanner(if (ok) "CAPTURE SAVED" else "CAPTURE FAILED", CAPTURE_BANNER_MS)
            }
        }
    }

    private fun onCaptureTap() {
        val sent = repository.sendCapture()
        if (!sent) {
            Log.w(TAG, "CAPTURE tap ignored: control channel not connected")
            showTransientBanner("NOT CONNECTED", CAPTURE_BANNER_MS)
        }
    }

    // ---------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------

    private fun renderGlassState(state: GlassStateMessage?) {
        if (state == null) {
            textBp.text = "BP --/--"
            textSpo2.text = "SpO2 --%"
            textHr.text = "HR --"
            textTemp.text = "TEMP -- C"
            textPatient.text = "NO PATIENT"
            textExamMode.text = "NONE"
            textRecording.text = ""
            textSimBadge.visibility = View.GONE
            recordingStartedAtElapsedMs = null
            return
        }

        textBp.text = if (state.bpSys != null && state.bpDia != null) {
            "BP ${state.bpSys.toInt()}/${state.bpDia.toInt()}"
        } else {
            "BP --/--"
        }
        textSpo2.text = state.spo2?.let { "SpO2 ${it.toInt()}%" } ?: "SpO2 --%"
        textHr.text = state.heartRate?.let { "HR ${it.toInt()}" } ?: "HR --"
        textTemp.text = state.temperature?.let { "TEMP %.1f C".format(it) } ?: "TEMP -- C"

        textPatient.text = formatPatientLabel(state)
        textExamMode.text = examModeLabel(state.examMode)
        textRecording.text = formatRecordingLabel(state)
        textSimBadge.visibility = if (state.simulated) View.VISIBLE else View.GONE
    }

    private fun renderCameraFrame(frame: Bitmap?) {
        if (frame == null) {
            imageCamera.visibility = View.GONE
            textEndoscopePlaceholder.visibility = View.VISIBLE
            return
        }
        imageCamera.setImageBitmap(frame)
        imageCamera.visibility = View.VISIBLE
        textEndoscopePlaceholder.visibility = View.GONE
    }

    private fun formatPatientLabel(state: GlassStateMessage): String {
        val name = state.patientDisplayName ?: return "NO PATIENT"
        val sexAge = listOfNotNull(state.sex, state.age?.toString()).joinToString("")
        return if (sexAge.isNotEmpty()) "$name / $sexAge" else name
    }

    private fun examModeLabel(examMode: String): String = when (examMode) {
        "OTOSCOPE_LEFT" -> "OTOSCOPE-L"
        "OTOSCOPE_RIGHT" -> "OTOSCOPE-R"
        "COLPOSCOPE" -> "COLPOSCOPE"
        else -> "NONE"
    }

    private fun formatRecordingLabel(state: GlassStateMessage): String {
        if (!state.recording) {
            recordingStartedAtElapsedMs = null
            return if (state.frozen) "FROZEN" else ""
        }
        // GlassState carries a boolean, not a start timestamp, so duration is
        // measured client-side from when Glass first observed recording=true.
        // Not server-authoritative -- see docs/glass_app.md "Known limitations".
        val startedAt = recordingStartedAtElapsedMs ?: SystemClock.elapsedRealtime().also {
            recordingStartedAtElapsedMs = it
        }
        val elapsedSec = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        return "● REC %02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    }

    private fun renderConnectionState(state: UniHubConnectionState) {
        val previous = lastRenderedConnectionState
        lastRenderedConnectionState = state

        when (state) {
            UniHubConnectionState.CONNECTED -> {
                textConnection.text = "● CONNECTED"
                textConnection.setTextColor(color(R.color.hud_connected))
                clearPersistentDisconnectedBanner()
                val wasDown = previous == UniHubConnectionState.DISCONNECTED ||
                    previous == UniHubConnectionState.RECONNECTING
                if (wasDown) {
                    showTransientBanner("RECONNECTED", RECONNECTED_BANNER_MS)
                }
            }
            UniHubConnectionState.CONNECTING -> {
                textConnection.text = "CONNECTING"
                textConnection.setTextColor(color(R.color.hud_connecting))
            }
            UniHubConnectionState.RECONNECTING -> {
                textConnection.text = "RECONNECTING"
                textConnection.setTextColor(color(R.color.hud_connecting))
            }
            UniHubConnectionState.DISCONNECTED -> {
                textConnection.text = "DISCONNECTED"
                textConnection.setTextColor(color(R.color.hud_disconnected))
                // Do not keep showing stale medical values (or a frozen
                // last camera frame) as current.
                renderGlassState(null)
                renderCameraFrame(null)
                showPersistentDisconnectedBanner()
            }
        }
    }

    // ---------------------------------------------------------------
    // Banner overlay (DISCONNECTED / RECONNECTED / CAPTURE SAVED)
    // ---------------------------------------------------------------

    private fun showTransientBanner(text: String, durationMs: Long) {
        bannerHandler.removeCallbacksAndMessages(null)
        persistentDisconnectedBannerShown = false
        textBanner.text = text
        textBanner.visibility = View.VISIBLE
        bannerHandler.postDelayed({
            if (!persistentDisconnectedBannerShown) {
                textBanner.visibility = View.GONE
            }
        }, durationMs)
    }

    private fun showPersistentDisconnectedBanner() {
        bannerHandler.removeCallbacksAndMessages(null)
        persistentDisconnectedBannerShown = true
        textBanner.text = "DISCONNECTED"
        textBanner.visibility = View.VISIBLE
    }

    private fun clearPersistentDisconnectedBanner() {
        if (persistentDisconnectedBannerShown) {
            persistentDisconnectedBannerShown = false
            textBanner.visibility = View.GONE
        }
    }

    private fun color(resId: Int) = androidx.core.content.ContextCompat.getColor(this, resId)

    companion object {
        private const val TAG = "GlassHudActivity"
        private const val EXTRA_HOST = "uni_hub_host"
        private const val EXTRA_PORT = "uni_hub_port"
        private const val CAPTURE_BANNER_MS = 1000L
        private const val RECONNECTED_BANNER_MS = 1500L
    }
}
