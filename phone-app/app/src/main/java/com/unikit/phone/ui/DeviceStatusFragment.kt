package com.unikit.phone.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.unikit.phone.R
import com.unikit.phone.model.DeviceStatusInfo
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Screen 10 -- see plan #6. Initial snapshot from GET /devices, then
 * live-updated from the device_status telemetry stream (repository's
 * deviceStatus map, fed by TelemetryClient's new parsing). Row order
 * matches the server's fixed device list (device_registry.py ALL_DEVICES)
 * so devices don't jump around as updates arrive.
 *
 * Deviation from the spec's literal field list: DeviceStatus
 * (app/models/device.py) doesn't carry a `sourceDevice` field -- only
 * device/state/lastSeen/quality/simulated -- so this shows the device
 * name plus lastSeen/quality/simulated, no separate sourceDevice column.
 */
class DeviceStatusFragment : Fragment(R.layout.fragment_device_status) {

    private var fragmentScope: CoroutineScope? = null
    private val adapter = DeviceStatusAdapter()

    private val deviceOrder = listOf(
        "UNI_HUB", "GLASS", "BLOOD_PRESSURE", "SPO2", "TEMPERATURE",
        "ECG", "STETHOSCOPE", "ENDOSCOPE", "PHONE_CAMERA",
    )

    // Baseline from GET /devices (covers STETHOSCOPE/ENDOSCOPE, which never
    // heartbeat and so never appear in the live telemetry map at all) and
    // the latest live telemetry map, merged on every render -- see
    // mergeAndRender() for why neither alone is a complete picture.
    private var restSnapshot: Map<String, DeviceStatusInfo> = emptyMap()
    private var liveSnapshot: Map<String, DeviceStatusInfo> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, repository)
        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerDevices)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        fragmentScope?.launch {
            try {
                restSnapshot = repository.getDevicesSnapshot().associateBy { it.device }
                render()
            } catch (e: Exception) {
                // Fall through to whatever telemetry has already delivered --
                // the live collector below keeps the screen useful even if
                // the initial REST snapshot fails.
            }
        }

        fragmentScope?.launch {
            repository.deviceStatus.collect { devices ->
                liveSnapshot = devices
                render()
            }
        }
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    /**
     * Live telemetry overrides the REST baseline entry-by-entry, but a
     * device the live stream has never mentioned (STETHOSCOPE/ENDOSCOPE)
     * still falls back to its REST baseline row instead of disappearing.
     */
    private fun render() {
        val merged = restSnapshot + liveSnapshot
        val ordered = deviceOrder.mapNotNull { merged[it] }
        adapter.submitList(ordered)
    }
}
