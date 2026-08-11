package com.unikit.phone.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.unikit.phone.R
import com.unikit.phone.state.ExamSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Screen 2 (dashboard) -- see plan #6. Every module fragment is pushed
 * from here and every one of them returns here on back (this fragment is
 * the FragmentManager back-stack root for the whole exam session, set via
 * MainActivity.showRoot() from PatientRegistrationFragment).
 */
class ExamHomeFragment : Fragment(R.layout.fragment_exam_home) {

    private var fragmentScope: CoroutineScope? = null

    private val modules = listOf(
        "OTOSCOPE" to { OtoscopeFragment() },
        "COLPOSCOPE" to { ColposcopeFragment() },
        "ECG" to { EcgFragment() },
        "SpO2 + TEMPERATURE" to { Spo2TemperatureFragment() },
        "BLOOD PRESSURE" to { BloodPressureFragment() },
        "HEART RATE" to { HeartRateFragment() },
        "STETHOSCOPE" to { StethoscopeFragment() },
        "DEVICE STATUS" to { DeviceStatusFragment() },
        "REPORT" to { ReportFragment() },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, (requireActivity() as MainActivity).repository)

        val container = view.findViewById<LinearLayout>(R.id.moduleContainer)
        container.removeAllViews()
        modules.chunked(2).forEach { row ->
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            row.forEach { (label, factory) ->
                rowLayout.addView(buildModuleCard(label) { (requireActivity() as MainActivity).pushFragment(factory()) })
            }
            if (row.size == 1) {
                // Balance the last odd card so it doesn't stretch full-width.
                rowLayout.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
            container.addView(rowLayout)
        }

        view.findViewById<Button>(R.id.buttonEndExamination).setOnClickListener { onEndExamination() }
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun buildModuleCard(label: String, onClick: () -> Unit): View {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 8, 8, 8)
            }
            radius = 6f
            cardElevation = 1f
            strokeWidth = 1
            strokeColor = ContextCompat.getColor(requireContext(), R.color.divider)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_white))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val text = TextView(requireContext()).apply {
            text = label
            textSize = 15f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setPadding(24, 40, 24, 40)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        card.addView(text)
        return card
    }

    private fun onEndExamination() {
        val session = ExamSession.current ?: run {
            (requireActivity() as MainActivity).showRoot(PatientRegistrationFragment())
            return
        }
        val activity = requireActivity() as MainActivity
        fragmentScope?.launch {
            try {
                activity.repository.endExam(session.examId)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "END EXAMINATION request failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                activity.repository.resetSessionBuffers()
                ExamSession.clear()
                activity.showRoot(PatientRegistrationFragment())
            }
        }
    }
}
