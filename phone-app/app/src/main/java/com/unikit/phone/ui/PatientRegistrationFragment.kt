package com.unikit.phone.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.unikit.phone.R
import com.unikit.phone.state.ExamSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 * Screen 1 (see ~/.claude/plans/curious-floating-flame.md #6). Collects
 * patient demographics, then POST /patients + POST /exams and stores the
 * result in ExamSession before handing off to ExamHomeFragment.
 *
 * Deviation from the spec's literal field list: the server's PatientCreate
 * model (app/models/patient.py) only has an `age: int | None` field, no
 * date-of-birth field -- so date of birth is collected here purely as a
 * friendlier input UX and converted to an integer age client-side before
 * the REST call. DOB itself is not sent to or stored by the server.
 * Patient photo is local-only UI polish (not part of the wire model, per
 * the task's explicit scope note) -- it's decoded into the preview
 * ImageView and otherwise discarded, never uploaded.
 */
class PatientRegistrationFragment : Fragment(R.layout.fragment_patient_registration) {

    private var fragmentScope: CoroutineScope? = null
    private var dobCalendar: Calendar? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imagePatientPhoto)?.setImageURI(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val inputDob = view.findViewById<TextInputEditText>(R.id.inputDob)
        inputDob.setOnClickListener { showDatePicker(inputDob) }

        view.findViewById<Button>(R.id.buttonChoosePhoto).setOnClickListener {
            pickPhoto.launch("image/*")
        }

        view.findViewById<Button>(R.id.buttonStartExamination).setOnClickListener { onStartExamination(view) }
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun showDatePicker(target: TextInputEditText) {
        val now = Calendar.getInstance()
        val initial = dobCalendar ?: Calendar.getInstance().apply { add(Calendar.YEAR, -30) }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day) }
                dobCalendar = picked
                target.setText(DATE_FORMAT.format(picked.time))
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = now.timeInMillis }.show()
    }

    private fun ageFromDob(dob: Calendar): Int {
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
        return age.coerceAtLeast(0)
    }

    private fun onStartExamination(root: View) {
        val name = root.findViewById<TextInputEditText>(R.id.inputName).text?.toString()?.trim().orEmpty()
        val patientIdInput = root.findViewById<TextInputEditText>(R.id.inputPatientId).text?.toString()?.trim().orEmpty()
        val sexGroup = root.findViewById<RadioGroup>(R.id.radioGroupSex)
        val textError = root.findViewById<TextView>(R.id.textError)
        val progress = root.findViewById<ProgressBar>(R.id.progressStarting)
        val button = root.findViewById<Button>(R.id.buttonStartExamination)

        textError.visibility = View.GONE

        if (name.isEmpty()) {
            textError.text = "Patient name is required."
            textError.visibility = View.VISIBLE
            return
        }

        val sex = when (sexGroup.checkedRadioButtonId) {
            R.id.radioSexM -> "M"
            R.id.radioSexF -> "F"
            R.id.radioSexOther -> "OTHER"
            else -> null
        }
        val age = dobCalendar?.let { ageFromDob(it) }
        val patientId = patientIdInput.ifEmpty { generatePatientId() }

        val activity = requireActivity() as MainActivity
        button.isEnabled = false
        progress.visibility = View.VISIBLE

        fragmentScope?.launch {
            try {
                activity.repository.createPatient(patientId, name, age, sex)
                val exam = activity.repository.createExam(patientId)
                activity.repository.resetSessionBuffers()
                ExamSession.start(
                    ExamSession.Data(
                        patientId = patientId,
                        examId = exam.examId,
                        displayName = name,
                        age = age,
                        sex = sex,
                    ),
                )
                activity.showRoot(ExamHomeFragment())
            } catch (e: Exception) {
                textError.text = "Could not start examination: ${e.message}"
                textError.visibility = View.VISIBLE
                button.isEnabled = true
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), "START EXAMINATION failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePatientId(): String {
        val suffix = Random.nextInt(0, 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
        return "P$suffix"
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
