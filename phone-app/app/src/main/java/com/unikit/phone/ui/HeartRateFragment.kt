package com.unikit.phone.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Screen 6 -- see plan #6. Current HR + raw PPG waveform (PPG_IR
 * preferred, falls back to PPG_RED if that's what's arriving) + HR
 * trend/min/max/avg computed client-side from repository.vitalsHistory
 * (an in-memory rolling buffer since exam start -- plan #8, no new server
 * storage).
 */
class HeartRateFragment : Fragment(R.layout.fragment_heart_rate) {

    private var fragmentScope: CoroutineScope? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, repository)
        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }

        observeVitals(view, repository)
        observeWaveform(view, repository)
        observeDeviceStatus(view, repository)
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun observeVitals(root: View, repository: UniHubRepository) {
        val textHr = root.findViewById<TextView>(R.id.textHrValue)
        val textQuality = root.findViewById<TextView>(R.id.textQuality)
        val textTimestamp = root.findViewById<TextView>(R.id.textTimestamp)
        val textMin = root.findViewById<TextView>(R.id.textHrMin)
        val textAvg = root.findViewById<TextView>(R.id.textHrAvg)
        val textMax = root.findViewById<TextView>(R.id.textHrMax)

        fragmentScope?.launch {
            repository.vitals.collect { v ->
                textHr.text = v?.heartRate?.let { it.toInt().toString() } ?: "--"
                textQuality.text = "Signal quality: ${v?.quality ?: "--"}"
                textTimestamp.text = "Updated: ${v?.timestamp ?: "--"}"
            }
        }
        fragmentScope?.launch {
            repository.vitalsHistory.collect { history ->
                val values = history.mapNotNull { it.heartRate }
                if (values.isEmpty()) {
                    textMin.text = "--"; textAvg.text = "--"; textMax.text = "--"
                } else {
                    textMin.text = values.min().toInt().toString()
                    textMax.text = values.max().toInt().toString()
                    textAvg.text = (values.sum() / values.size).toInt().toString()
                }
            }
        }
    }

    private fun observeWaveform(root: View, repository: UniHubRepository) {
        val waveform = root.findViewById<WaveformView>(R.id.waveformPpg)
        fragmentScope?.launch {
            repository.rawSignal.collect { signal ->
                if (signal.signal != "PPG_IR" && signal.signal != "PPG_RED") return@collect
                waveform.pushSamples(signal.samples.map { it.toFloat() })
            }
        }
    }

    private fun observeDeviceStatus(root: View, repository: UniHubRepository) {
        val textDeviceState = root.findViewById<TextView>(R.id.textDeviceState)
        fragmentScope?.launch {
            repository.deviceStatus.collect { devices ->
                val spo2Device = devices["SPO2"]
                textDeviceState.text = "Device: ${spo2Device?.state ?: "DISCONNECTED"}"
            }
        }
    }
}
