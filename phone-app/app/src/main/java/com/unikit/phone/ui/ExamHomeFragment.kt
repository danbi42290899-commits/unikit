package com.unikit.phone.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
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
 *
 * Redesigned as a big-card swipeable carousel (RecyclerView + PagerSnapHelper,
 * one full-width card per screen, matching the HTML preview) instead of the
 * original 2-column card grid -- module destinations are unchanged, only
 * the entry navigation's look and Otoscope-first ordering changed.
 */
class ExamHomeFragment : Fragment(R.layout.fragment_exam_home) {

    private var fragmentScope: CoroutineScope? = null

    private val modules = listOf(
        ModuleCardSpec("👂", "Otoscope", "Live camera capture", ModuleCardSpec.Hue.BLUE) { OtoscopeFragment() },
        ModuleCardSpec("🔬", "Colposcope", "Live camera capture", ModuleCardSpec.Hue.BLUE) { ColposcopeFragment() },
        ModuleCardSpec("🩺", "Blood Pressure", "Cuff readings", ModuleCardSpec.Hue.BLUE) { BloodPressureFragment() },
        ModuleCardSpec("〰️", "ECG", "Rhythm strip", ModuleCardSpec.Hue.GREEN) { EcgFragment() },
        ModuleCardSpec("💓", "Heart Rate", "Pulse monitor", ModuleCardSpec.Hue.GREEN) { HeartRateFragment() },
        ModuleCardSpec("🌡️", "SpO2 + Temperature", "Oxygen & temperature", ModuleCardSpec.Hue.SKY) { Spo2TemperatureFragment() },
        ModuleCardSpec("🎧", "Stethoscope", "Auscultation audio", ModuleCardSpec.Hue.SKY) { StethoscopeFragment() },
        ModuleCardSpec("📶", "Device Status", "Connected sensors", ModuleCardSpec.Hue.SKY) { DeviceStatusFragment() },
        ModuleCardSpec("📋", "Report", "Exam summary", ModuleCardSpec.Hue.GREEN) { ReportFragment() },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        VitalsHeaderBinder.bind(view.findViewById(R.id.header), fragmentScope!!, (requireActivity() as MainActivity).repository)

        val carousel = view.findViewById<RecyclerView>(R.id.moduleCarousel)
        val dotsContainer = view.findViewById<LinearLayout>(R.id.carouselDots)
        val adapter = ModuleCarouselAdapter(modules) { spec ->
            (requireActivity() as MainActivity).pushFragment(spec.factory())
        }
        carousel.adapter = adapter
        carousel.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(carousel)
        buildDots(dotsContainer)
        carousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val lm = rv.layoutManager as LinearLayoutManager
                val centerView = snapHelper.findSnapView(lm)
                val position = centerView?.let { lm.getPosition(it) } ?: lm.findFirstVisibleItemPosition()
                updateDots(dotsContainer, position)
            }
        })

        view.findViewById<Button>(R.id.buttonEndExamination).setOnClickListener { onEndExamination() }
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun buildDots(container: LinearLayout) {
        container.removeAllViews()
        modules.forEach {
            val dot = View(requireContext()).apply {
                val size = resources.displayMetrics.density * 7
                layoutParams = LinearLayout.LayoutParams(size.toInt(), size.toInt()).apply {
                    marginStart = (resources.displayMetrics.density * 3).toInt()
                    marginEnd = (resources.displayMetrics.density * 3).toInt()
                }
                setBackgroundResource(R.drawable.shape_dot)
            }
            container.addView(dot)
        }
        updateDots(container, 0)
    }

    private fun updateDots(container: LinearLayout, activeIndex: Int) {
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val colorRes = if (i == activeIndex) R.color.accent_primary else R.color.divider
            dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
        }
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
