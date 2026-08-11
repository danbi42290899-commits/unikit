package com.unikit.phone.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen 5 -- see plan #6. Subscribes raw_signal where signal=="ECG" for
 * the live waveform; START/STOP reuse the existing generic
 * START_RECORDING/STOP_RECORDING commands (recording is mode-independent
 * on GlassState, no new server-side ECG exam-mode needed). SAVE logs an
 * event only in this pass -- no raw waveform persistence endpoint exists
 * yet (see plan #8), so it's an honest toast rather than a silent no-op
 * or a pretend file save.
 */
class EcgFragment : Fragment(R.layout.fragment_ecg) {

    private var fragmentScope: CoroutineScope? = null
    private var recordingStartedAtElapsedMs: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, repository)

        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }
        view.findViewById<Button>(R.id.buttonRecordToggle).setOnClickListener {
            val recording = repository.glassState.value?.recording ?: false
            if (!repository.sendCommand(if (recording) "STOP_RECORDING" else "START_RECORDING")) {
                Toast.makeText(requireContext(), "NOT CONNECTED", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.buttonSave).setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Recording logged, waveform export not yet implemented",
                Toast.LENGTH_LONG,
            ).show()
        }

        observeRawSignal(view, repository)
        observeGlassStateAndDeviceStatus(view, repository)
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun observeRawSignal(root: View, repository: UniHubRepository) {
        val waveform = root.findViewById<WaveformView>(R.id.waveformEcg)
        val textSampleRate = root.findViewById<TextView>(R.id.textSampleRate)
        val textQuality = root.findViewById<TextView>(R.id.textQuality)
        fragmentScope?.launch {
            repository.rawSignal.collect { signal ->
                if (signal.signal != "ECG") return@collect
                waveform.pushSamples(signal.samples.map { it.toFloat() })
                textSampleRate.text = "Sample rate: ${signal.sampleRate?.let { "${it.toInt()} Hz" } ?: "--"}"
                textQuality.text = "Signal quality: ${signal.quality}"
            }
        }
    }

    private fun observeGlassStateAndDeviceStatus(root: View, repository: UniHubRepository) {
        val textRecordingState = root.findViewById<TextView>(R.id.textRecordingState)
        val buttonRecordToggle = root.findViewById<Button>(R.id.buttonRecordToggle)
        val textDeviceState = root.findViewById<TextView>(R.id.textDeviceState)

        fragmentScope?.launch {
            repository.glassState.collect { state ->
                buttonRecordToggle.text = if (state?.recording == true) "STOP ECG" else "START ECG"
            }
        }
        fragmentScope?.launch {
            repository.deviceStatus.collect { devices ->
                val ecg = devices["ECG"]
                textDeviceState.text = "ECG device: ${ecg?.state ?: "DISCONNECTED"}"
            }
        }
        // Client-side recording duration, same convention as Glass's
        // formatRecordingLabel() -- GlassState only carries a boolean, not
        // a start timestamp, so duration is measured from when this
        // screen first observed recording=true (not server-authoritative).
        fragmentScope?.launch {
            while (isActive) {
                val recording = repository.glassState.value?.recording == true
                if (recording) {
                    val startedAt = recordingStartedAtElapsedMs ?: SystemClock.elapsedRealtime().also {
                        recordingStartedAtElapsedMs = it
                    }
                    val elapsedSec = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
                    textRecordingState.text = "● RECORDING %02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                } else {
                    recordingStartedAtElapsedMs = null
                    textRecordingState.text = "NOT RECORDING"
                }
                delay(500)
            }
        }
    }
}
