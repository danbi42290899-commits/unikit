package com.unikit.phone.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import com.unikit.phone.model.VitalsSnapshot
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Screen 8 -- see plan #6. SYS/DIA come straight off the vitals stream;
 * MAP is computed client-side ((2*DIA+SYS)/3, a standard approximation)
 * since the server doesn't send it directly. "Pulse" reuses the same
 * vitals.heartRate field the server sends -- there is no separate cuff
 * pulse reading in the mock data, so this is labeled from the same HR
 * value rather than fabricating an independent pulse sensor. No raw cuff
 * pressure waveform is shown: BP_CUFF_PRESSURE is a declared signal name
 * in app/models/vitals.py but no mock provider ever emits it (see plan
 * #6) -- showing one here would be fabricating data, so it's omitted
 * entirely rather than rendering an empty/fake waveform.
 */
class BloodPressureFragment : Fragment(R.layout.fragment_blood_pressure) {

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
        observeHistory(view, repository)
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun observeVitals(root: View, repository: UniHubRepository) {
        val textSys = root.findViewById<TextView>(R.id.textSys)
        val textDia = root.findViewById<TextView>(R.id.textDia)
        val textMap = root.findViewById<TextView>(R.id.textMap)
        val textPulse = root.findViewById<TextView>(R.id.textPulse)
        val textTimestamp = root.findViewById<TextView>(R.id.textTimestamp)

        fragmentScope?.launch {
            repository.vitals.collect { v ->
                textSys.text = v?.bpSys?.let { it.toInt().toString() } ?: "--"
                textDia.text = v?.bpDia?.let { it.toInt().toString() } ?: "--"
                textMap.text = mapOrDash(v)
                textPulse.text = v?.heartRate?.let { "${it.toInt()} bpm" } ?: "--"
                textTimestamp.text = v?.timestamp ?: "--"
            }
        }
    }

    private fun mapOrDash(v: VitalsSnapshot?): String {
        val sys = v?.bpSys ?: return "--"
        val dia = v.bpDia ?: return "--"
        val map = (2 * dia + sys) / 3.0
        return map.roundToInt().toString()
    }

    private fun observeHistory(root: View, repository: UniHubRepository) {
        val container = root.findViewById<LinearLayout>(R.id.historyContainer)
        fragmentScope?.launch {
            repository.vitalsHistory.collect { history ->
                val readings = history.filter { it.bpSys != null && it.bpDia != null }.takeLast(15).reversed()
                container.removeAllViews()
                if (readings.isEmpty()) {
                    container.addView(rowText("No readings yet this exam."))
                    return@collect
                }
                readings.forEach { v ->
                    container.addView(
                        rowText("${v.timestamp ?: "--"}   ${v.bpSys!!.toInt()}/${v.bpDia!!.toInt()} mmHg"),
                    )
                }
            }
        }
    }

    private fun rowText(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setPadding(8, 6, 8, 6)
        setTextColor(resources.getColor(R.color.text_primary, null))
    }
}
