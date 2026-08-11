package com.unikit.phone.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.unikit.phone.R
import com.unikit.phone.network.UniHubConfig
import com.unikit.phone.repository.UniHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single Fragment host for the whole exam workflow (see
 * ~/.claude/plans/curious-floating-flame.md #1/#6 for why plain
 * FragmentManager transactions instead of Navigation Component).
 *
 * Owns the process-lifetime UNI-HUB connection (UniHubRepository) and the
 * shared OkHttpClient -- every Fragment reaches these via the two
 * accessors below rather than creating its own.
 */
class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var repository: UniHubRepository
        private set
    lateinit var httpClient: OkHttpClient
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyConfigOverridesFromIntent()

        httpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        repository = UniHubRepository(httpClient, activityScope)
        repository.start()

        if (savedInstanceState == null) {
            showRoot(PatientRegistrationFragment())
        }

        Log.i(TAG, "UNI-KIT Phone started; telemetry=${UniHubConfig.telemetryUrl} control=${UniHubConfig.controlUrl}")
    }

    override fun onDestroy() {
        repository.stop()
        activityScope.cancel()
        super.onDestroy()
    }

    /** Same override mechanism as glass-app's GlassHudActivity. */
    private fun applyConfigOverridesFromIntent() {
        intent?.getStringExtra(EXTRA_HOST)?.let {
            Log.i(TAG, "UNI_HUB_HOST overridden via intent extra: $it")
            UniHubConfig.host = it
        }
        if (intent?.hasExtra(EXTRA_PORT) == true) {
            val port = intent.getIntExtra(EXTRA_PORT, UniHubConfig.port)
            Log.i(TAG, "UNI_HUB_PORT overridden via intent extra: $port")
            UniHubConfig.port = port
        }
    }

    /** Pushes a module screen on top of the current one; back returns to it. */
    fun pushFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Clears the whole back stack and makes `fragment` the new root --
     * used for the Registration->ExamHome and ExamHome->Registration
     * transitions so the user can never back-navigate into a stale exam.
     */
    fun showRoot(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /**
     * Returns to whatever pushed the current module screen -- always
     * ExamHomeFragment in this app's navigation tree, since every module
     * fragment is pushed directly from there (see plan #6).
     */
    fun popFragment() {
        supportFragmentManager.popBackStack()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_HOST = "uni_hub_host"
        private const val EXTRA_PORT = "uni_hub_port"
    }
}
