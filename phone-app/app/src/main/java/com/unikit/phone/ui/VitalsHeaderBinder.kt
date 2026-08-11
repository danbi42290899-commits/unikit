package com.unikit.phone.ui

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.unikit.phone.R
import com.unikit.phone.model.VitalsSnapshot
import com.unikit.phone.repository.UniHubRepository
import com.unikit.phone.state.ExamSession
import com.unikit.phone.state.UniHubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Binds include_header_patient_vitals.xml against ExamSession + the
 * shared UniHubRepository. Every module fragment includes that layout and
 * calls this once from onViewCreated -- keeps every screen showing the
 * exact same patient/vitals/connection state instead of each re-deriving
 * it (see plan #5's "single source of truth" requirement).
 *
 * Mirrors Glass's own "no stale values" convention: vitals are only
 * rendered from the latest snapshot while connectionState == CONNECTED
 * (which already folds in link-staleness, see UniHubRepository); the
 * instant the link is stale/down this blanks to "--" rather than holding
 * the last-known numbers.
 */
object VitalsHeaderBinder {

    fun bind(headerRoot: View, scope: CoroutineScope, repository: UniHubRepository) {
        val textName = headerRoot.findViewById<TextView>(R.id.headerPatientName)
        val textMeta = headerRoot.findViewById<TextView>(R.id.headerPatientMeta)
        val textConn = headerRoot.findViewById<TextView>(R.id.headerConnection)
        val textBp = headerRoot.findViewById<TextView>(R.id.headerBp)
        val textSpo2 = headerRoot.findViewById<TextView>(R.id.headerSpo2)
        val textHr = headerRoot.findViewById<TextView>(R.id.headerHr)
        val textTemp = headerRoot.findViewById<TextView>(R.id.headerTemp)

        val session = ExamSession.current
        textName.text = session?.displayName ?: "NO PATIENT"
        textMeta.text = if (session != null) {
            listOfNotNull(session.sex, session.age?.toString(), session.patientId, session.examId)
                .joinToString(" · ")
        } else {
            ""
        }

        scope.launch {
            repository.connectionState.collect { state ->
                when (state) {
                    UniHubConnectionState.CONNECTED -> {
                        textConn.text = headerRoot.context.getString(R.string.status_connected)
                        textConn.setTextColor(color(headerRoot, R.color.status_connected))
                    }
                    UniHubConnectionState.CONNECTING, UniHubConnectionState.RECONNECTING -> {
                        textConn.text = headerRoot.context.getString(R.string.status_connecting)
                        textConn.setTextColor(color(headerRoot, R.color.status_connecting))
                    }
                    UniHubConnectionState.DISCONNECTED -> {
                        textConn.text = headerRoot.context.getString(R.string.status_disconnected)
                        textConn.setTextColor(color(headerRoot, R.color.status_disconnected))
                    }
                }
            }
        }

        scope.launch {
            repository.connectionState.combine(repository.vitals) { connState, vitals ->
                if (connState == UniHubConnectionState.CONNECTED) vitals else null
            }.collect { v -> renderVitals(v, textBp, textSpo2, textHr, textTemp) }
        }
    }

    private fun renderVitals(
        v: VitalsSnapshot?,
        textBp: TextView,
        textSpo2: TextView,
        textHr: TextView,
        textTemp: TextView,
    ) {
        textBp.text = if (v?.bpSys != null && v.bpDia != null) {
            "${v.bpSys.toInt()}/${v.bpDia.toInt()}"
        } else {
            "--/--"
        }
        textSpo2.text = v?.spo2?.let { "${it.toInt()}%" } ?: "--%"
        textHr.text = v?.heartRate?.let { "${it.toInt()}" } ?: "--"
        textTemp.text = v?.temperature?.let { "%.1f C".format(it) } ?: "-- C"
    }

    private fun color(view: View, resId: Int) = ContextCompat.getColor(view.context, resId)
}
