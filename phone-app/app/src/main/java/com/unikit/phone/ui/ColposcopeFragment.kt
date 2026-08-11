package com.unikit.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import com.unikit.phone.camera.PhoneCameraXProvider
import com.unikit.phone.model.GlassStateMessage
import com.unikit.phone.network.CameraUploadClient
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Screen 4 -- see plan #6. Same camera architecture as Otoscope, no
 * laterality control. Sends SET_COLPOSCOPE once on entry so Glass/UNI-HUB
 * immediately reflect the exam mode without waiting for a CAPTURE.
 */
class ColposcopeFragment : Fragment(R.layout.fragment_colposcope) {

    private var fragmentScope: CoroutineScope? = null
    private var cameraProvider: PhoneCameraXProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCameraIfPossible() else showCameraStatus("CAMERA PERMISSION DENIED")
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, repository)

        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }
        view.findViewById<Button>(R.id.buttonCapture).setOnClickListener {
            sendOrWarn(repository, "CAPTURE")
        }
        view.findViewById<Button>(R.id.buttonRecordToggle).setOnClickListener {
            val recording = repository.glassState.value?.recording ?: false
            sendOrWarn(repository, if (recording) "STOP_RECORDING" else "START_RECORDING")
        }
        view.findViewById<Button>(R.id.buttonFreezeToggle).setOnClickListener {
            val frozen = repository.glassState.value?.frozen ?: false
            sendOrWarn(repository, if (frozen) "UNFREEZE" else "FREEZE")
        }

        observeGlassState(view, repository)
        observeCommandAcks(repository)
        sendOrWarn(repository, "SET_COLPOSCOPE")
        ensureCameraPermissionThenStart()
    }

    override fun onDestroyView() {
        cameraProvider?.stop()
        cameraProvider = null
        cameraExecutor.shutdown()
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun sendOrWarn(repository: UniHubRepository, command: String) {
        if (!repository.sendCommand(command)) {
            Toast.makeText(requireContext(), "NOT CONNECTED", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeGlassState(root: View, repository: UniHubRepository) {
        val textMode = root.findViewById<TextView>(R.id.textModeLabel)
        val buttonRecord = root.findViewById<Button>(R.id.buttonRecordToggle)
        val buttonFreeze = root.findViewById<Button>(R.id.buttonFreezeToggle)
        fragmentScope?.launch {
            repository.glassState.collect { state: GlassStateMessage? ->
                textMode.text = examModeLabel(state?.examMode ?: "NONE")
                buttonRecord.text = if (state?.recording == true) "STOP\nRECORDING" else "START\nRECORDING"
                buttonFreeze.text = if (state?.frozen == true) "UNFREEZE" else "FREEZE"
            }
        }
    }

    private fun observeCommandAcks(repository: UniHubRepository) {
        fragmentScope?.launch {
            repository.commandAcks.collect { ack ->
                if (ack.command == "CAPTURE") {
                    val message = if (ack.ok) {
                        "CAPTURE SAVED (${ack.detail["mediaId"] ?: "?"})"
                    } else {
                        "CAPTURE FAILED: ${ack.detail["reason"] ?: "unknown reason"}"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun examModeLabel(examMode: String): String = when (examMode) {
        "OTOSCOPE_LEFT" -> "OTOSCOPE-L"
        "OTOSCOPE_RIGHT" -> "OTOSCOPE-R"
        "COLPOSCOPE" -> "COLPOSCOPE"
        else -> "NONE"
    }

    private fun ensureCameraPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraIfPossible()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraIfPossible() {
        val view = view ?: return
        val activity = requireActivity() as MainActivity
        val uploadClient = CameraUploadClient(activity.httpClient)
        val provider = PhoneCameraXProvider(requireContext(), cameraExecutor, uploadClient)
        cameraProvider = provider
        provider.start(viewLifecycleOwner, view.findViewById<PreviewView>(R.id.cameraPreview))
        showCameraStatus("")
    }

    private fun showCameraStatus(text: String) {
        view?.findViewById<TextView>(R.id.textCameraStatus)?.text = text
    }
}
