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
 * Screen 7 -- see plan #6. SpO2/HR/Temperature snapshot, both RED and IR
 * PPG waveforms, and a client-side temperature trend (same rolling-buffer
 * approach as HeartRateFragment -- plan #8, no new server storage).
 */
class Spo2TemperatureFragment : Fragment(R.layout.fragment_spo2_temperature) {

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
        observeWaveforms(view, repository)
        observeDeviceStatus(view, repository)
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun observeVitals(root: View, repository: UniHubRepository) {
        val textSpo2 = root.findViewById<TextView>(R.id.textSpo2Value)
        val textHr = root.findViewById<TextView>(R.id.textHrValue)
        val textTemp = root.findViewById<TextView>(R.id.textTempValue)
        val textQuality = root.findViewById<TextView>(R.id.textQuality)
        val textTimestamp = root.findViewById<TextView>(R.id.textTimestamp)
        val textTempMin = root.findViewById<TextView>(R.id.textTempMin)
        val textTempAvg = root.findViewById<TextView>(R.id.textTempAvg)
        val textTempMax = root.findViewById<TextView>(R.id.textTempMax)

        fragmentScope?.launch {
            repository.vitals.collect { v ->
                textSpo2.text = v?.spo2?.let { it.toInt().toString() } ?: "--"
                textHr.text = v?.heartRate?.let { it.toInt().toString() } ?: "--"
                textTemp.text = v?.temperature?.let { "%.1f".format(it) } ?: "--"
                textQuality.text = "Signal quality: ${v?.quality ?: "--"}"
                textTimestamp.text = "Updated: ${v?.timestamp ?: "--"}"
            }
        }
        fragmentScope?.launch {
            repository.vitalsHistory.collect { history ->
                val values = history.mapNotNull { it.temperature }
                if (values.isEmpty()) {
                    textTempMin.text = "--"; textTempAvg.text = "--"; textTempMax.text = "--"
                } else {
                    textTempMin.text = "%.1f".format(values.min())
                    textTempMax.text = "%.1f".format(values.max())
                    textTempAvg.text = "%.1f".format(values.sum() / values.size)
                }
            }
        }
    }

    private fun observeWaveforms(root: View, repository: UniHubRepository) {
        val waveformRed = root.findViewById<WaveformView>(R.id.waveformPpgRed)
        val waveformIr = root.findViewById<WaveformView>(R.id.waveformPpgIr)
        fragmentScope?.launch {
            repository.rawSignal.collect { signal ->
                when (signal.signal) {
                    "PPG_RED" -> waveformRed.pushSamples(signal.samples.map { it.toFloat() })
                    "PPG_IR" -> waveformIr.pushSamples(signal.samples.map { it.toFloat() })
                }
            }
        }
    }

    private fun observeDeviceStatus(root: View, repository: UniHubRepository) {
        val textDeviceState = root.findViewById<TextView>(R.id.textDeviceState)
        fragmentScope?.launch {
            repository.deviceStatus.collect { devices ->
                val spo2 = devices["SPO2"]?.state ?: "DISCONNECTED"
                val temp = devices["TEMPERATURE"]?.state ?: "DISCONNECTED"
                textDeviceState.text = "SpO2: $spo2  Β·  Temperature: $temp"
            }
        }
    }
}
