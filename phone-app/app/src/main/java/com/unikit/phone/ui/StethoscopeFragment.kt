package com.unikit.phone.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Screen 9 -- see plan #6/#8. STETHOSCOPE has no mock provider or
 * heartbeat producer anywhere in the server today (see
 * server/app/state/device_registry.py -- it stays permanently
 * DISCONNECTED), so this renders as an honest DISCONNECTED placeholder
 * rather than fabricating audio or a waveform. LISTEN/RECORD/STOP are
 * present (per the spec's field list) but disabled, with a toast on tap
 * as a fallback in case a future change re-enables them without updating
 * this screen.
 */
class StethoscopeFragment : Fragment(R.layout.fragment_stethoscope) {

    private var fragmentScope: CoroutineScope? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, repository)
        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }

        val notConnected = View.OnClickListener {
            Toast.makeText(requireContext(), "Stethoscope device not connected", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.buttonListen).setOnClickListener(notConnected)
        view.findViewById<Button>(R.id.buttonRecord).setOnClickListener(notConnected)
        view.findViewById<Button>(R.id.buttonStop).setOnClickListener(notConnected)

        fragmentScope?.launch {
            repository.deviceStatus.collect { devices ->
                view.findViewById<TextView>(R.id.textDeviceState).text =
                    devices["STETHOSCOPE"]?.state ?: "DISCONNECTED"
            }
        }
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }
}
