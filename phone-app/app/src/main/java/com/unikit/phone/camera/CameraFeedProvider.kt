package com.unikit.phone.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * Abstraction over "whatever camera source is currently feeding the
 * Otoscope/Colposcope live view + /ws/camera relay". Today there is
 * exactly one implementation, PhoneCameraXProvider (the phone's own back
 * camera) -- mirrors the server's SensorProvider ABC pattern (interface
 * defined, only the current implementation exists). This is what lets a
 * future real endoscope camera (a PiEndoscopeCameraProvider) get wired in
 * later without Otoscope/ColposcopeFragment needing a redesign; that
 * implementation does not exist yet and is out of scope for this pass
 * (see ~/.claude/plans/curious-floating-flame.md #5/#8).
 */
interface CameraFeedProvider {
    /** Binds the camera to the given lifecycle and starts rendering into previewView. */
    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView)

    /** Unbinds the camera. Safe to call even if start() was never called. */
    fun stop()
}
