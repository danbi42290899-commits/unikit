package com.unikit.phone.state

/**
 * Single in-memory holder of the active patient/exam context. Every
 * Fragment reads patientId/examId/patient info from here -- none keep an
 * independent copy (see ~/.claude/plans/curious-floating-flame.md #5).
 *
 * A plain object singleton rather than an androidx ViewModel: this app's
 * offline gradle cache has no fragment-ktx/lifecycle-viewmodel-ktx
 * (`by viewModels()` isn't available), and a single-Activity app with one
 * exam active at a time doesn't need ViewModel's
 * configuration-change/process-death survival machinery -- MainActivity
 * already owns the process lifetime that matters here.
 */
object ExamSession {
    data class Data(
        val patientId: String,
        val examId: String,
        val displayName: String,
        val age: Int?,
        val sex: String?,
    )

    @Volatile
    var current: Data? = null
        private set

    fun start(data: Data) {
        current = data
    }

    /** Called on END EXAMINATION -- returns to PatientRegistrationFragment with a clean slate. */
    fun clear() {
        current = null
    }
}
