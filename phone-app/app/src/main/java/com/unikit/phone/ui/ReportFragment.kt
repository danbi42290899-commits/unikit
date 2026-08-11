package com.unikit.phone.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.unikit.phone.R
import com.unikit.phone.model.EventLogEntry
import com.unikit.phone.model.MediaItem
import com.unikit.phone.repository.UniHubRepository
import com.unikit.phone.state.ExamSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 11 -- see plan #6. Never "DIAGNOSTIC REPORT", no automatic
 * diagnosis anywhere -- this only assembles what was actually captured/
 * recorded this exam.
 *
 * Otoscope/colposcope thumbnails come from GET /exams/{examId}/media
 * (server/app/routers/media.py), grouped by mode+laterality. Thumbnails
 * are loaded off-thread with plain BitmapFactory + OkHttp bytes (no
 * Glide/Coil -- neither is cached, see plan #1) since relay-captured
 * frames are already small JPEGs.
 *
 * ECG/Stethoscope sections show event-log entries only: there is no GET
 * /events REST endpoint (see docs/api_spec.md's "Not implemented"), so
 * this can only show START_RECORDING/STOP_RECORDING events the app
 * happened to observe live over /ws/telemetry during this session
 * (repository.events) -- explicitly labeled "not yet available" for the
 * waveform/audio itself rather than left looking broken, per plan #6.
 */
class ReportFragment : Fragment(R.layout.fragment_report) {

    private var fragmentScope: CoroutineScope? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repository = (requireActivity() as MainActivity).repository

        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            (requireActivity() as MainActivity).popFragment()
        }

        loadHeader(view, repository)
        loadVitalsSnapshot(view, repository)
        loadMedia(view, repository)
        loadEcgEvents(view, repository)
    }

    override fun onDestroyView() {
        fragmentScope?.cancel()
        fragmentScope = null
        super.onDestroyView()
    }

    private fun loadHeader(root: View, repository: UniHubRepository) {
        val session = ExamSession.current
        val textName = root.findViewById<TextView>(R.id.textPatientName)
        val textMeta = root.findViewById<TextView>(R.id.textPatientMeta)
        val textTimes = root.findViewById<TextView>(R.id.textExamTimes)

        textName.text = session?.displayName ?: "NO PATIENT"
        textMeta.text = listOfNotNull(session?.sex, session?.age?.toString(), session?.patientId)
            .joinToString(" · ")

        val examId = session?.examId ?: return
        fragmentScope?.launch {
            try {
                val exam = repository.getExam(examId)
                textTimes.text = "Exam ${exam.examId}  ·  ${exam.status}\n" +
                    "Started: ${exam.startedAt ?: "--"}\n" +
                    "Ended: ${exam.endedAt ?: "in progress"}"
            } catch (e: Exception) {
                textTimes.text = "Exam ${examId} (could not load timing: ${e.message})"
            }
        }
    }

    private fun loadVitalsSnapshot(root: View, repository: UniHubRepository) {
        val v = repository.vitals.value
        root.findViewById<TextView>(R.id.reportBp).text =
            if (v?.bpSys != null && v.bpDia != null) "${v.bpSys.toInt()}/${v.bpDia.toInt()}" else "--/--"
        root.findViewById<TextView>(R.id.reportSpo2).text = v?.spo2?.let { "${it.toInt()}%" } ?: "--%"
        root.findViewById<TextView>(R.id.reportHr).text = v?.heartRate?.let { "${it.toInt()}" } ?: "--"
        root.findViewById<TextView>(R.id.reportTemp).text = v?.temperature?.let { "%.1f".format(it) } ?: "--"
    }

    private fun loadMedia(root: View, repository: UniHubRepository) {
        val examId = ExamSession.current?.examId ?: return
        val leftContainer = root.findViewById<LinearLayout>(R.id.otoscopeLeftContainer)
        val rightContainer = root.findViewById<LinearLayout>(R.id.otoscopeRightContainer)
        val colpoContainer = root.findViewById<LinearLayout>(R.id.colposcopeContainer)

        fragmentScope?.launch {
            try {
                val media = repository.getExamMedia(examId)
                media.filter { it.mode == "OTOSCOPE" && it.laterality == "LEFT" }
                    .forEach { addThumbnail(leftContainer, it, repository) }
                media.filter { it.mode == "OTOSCOPE" && it.laterality == "RIGHT" }
                    .forEach { addThumbnail(rightContainer, it, repository) }
                media.filter { it.mode == "COLPOSCOPE" }
                    .forEach { addThumbnail(colpoContainer, it, repository) }

                if (media.none { it.mode == "OTOSCOPE" && it.laterality == "LEFT" }) {
                    leftContainer.addView(emptyLabel("No left ear captures this exam."))
                }
                if (media.none { it.mode == "OTOSCOPE" && it.laterality == "RIGHT" }) {
                    rightContainer.addView(emptyLabel("No right ear captures this exam."))
                }
                if (media.none { it.mode == "COLPOSCOPE" }) {
                    colpoContainer.addView(emptyLabel("No colposcope captures this exam."))
                }
            } catch (e: Exception) {
                leftContainer.addView(emptyLabel("Could not load media: ${e.message}"))
            }
        }
    }

    private fun addThumbnail(container: LinearLayout, item: MediaItem, repository: UniHubRepository) {
        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(96, 96).apply { setMargins(6, 6, 6, 6) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(resources.getColor(R.color.divider, null))
        }
        container.addView(imageView)
        fragmentScope?.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { repository.fetchMediaBytes(item.fileUrl) }
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bitmap != null) imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Leave the placeholder background -- a failed thumbnail
                // load shouldn't break the rest of the report.
            }
        }
    }

    private fun emptyLabel(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 12f
        setTextColor(resources.getColor(R.color.text_secondary, null))
        setPadding(8, 32, 8, 8)
    }

    private fun loadEcgEvents(root: View, repository: UniHubRepository) {
        val container = root.findViewById<LinearLayout>(R.id.ecgEventsContainer)
        val relevant = repository.events.value.filter {
            it.eventType in setOf("START_RECORDING", "STOP_RECORDING", "CAPTURE")
        }
        container.removeAllViews()
        if (relevant.isEmpty()) {
            container.addView(emptyLabel("No recording/capture events observed this session."))
            return
        }
        relevant.forEach { e: EventLogEntry ->
            container.addView(
                TextView(requireContext()).apply {
                    text = "${e.timestamp ?: "--"}   ${e.eventType}"
                    textSize = 13f
                    setPadding(8, 6, 8, 6)
                    setTextColor(resources.getColor(R.color.text_primary, null))
                },
            )
        }
    }
}
