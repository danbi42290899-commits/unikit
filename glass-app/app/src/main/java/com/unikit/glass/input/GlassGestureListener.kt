package com.unikit.glass.input

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Touchpad gesture routing for Glass EE2. The touchpad delivers standard
 * Android MotionEvents (see GlassHudActivity, which forwards them into a
 * GestureDetector built with this listener) -- no GDK / special Glass API
 * needed.
 *
 * Phase 2 only wires up TAP -> CAPTURE. The other callbacks are
 * implemented and logged (so the gesture pipeline is provably working end
 * to end) but intentionally call a no-op stub, per the Phase 2 scope:
 *   double tap    -> FREEZE / UNFREEZE   (not yet)
 *   long press    -> START / STOP RECORDING (not yet)
 *   swipe forward -> navigation (not yet)
 *   swipe backward -> navigation (not yet)
 */
class GlassGestureListener(
    private val onTap: () -> Unit,
    private val onDoubleTapStub: () -> Unit = {},
    private val onLongPressStub: () -> Unit = {},
    private val onSwipeForwardStub: () -> Unit = {},
    private val onSwipeBackwardStub: () -> Unit = {},
) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        Log.i(TAG, "tap -> CAPTURE")
        onTap()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        Log.d(TAG, "double tap (reserved for FREEZE/UNFREEZE -- not implemented until a later phase)")
        onDoubleTapStub()
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        Log.d(TAG, "long press (reserved for START/STOP_RECORDING -- not implemented until a later phase)")
        onLongPressStub()
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        if (abs(velocityX) <= abs(velocityY)) return false
        if (velocityX > 0) {
            Log.d(TAG, "swipe forward (reserved for navigation -- not implemented until a later phase)")
            onSwipeForwardStub()
        } else {
            Log.d(TAG, "swipe backward (reserved for navigation -- not implemented until a later phase)")
            onSwipeBackwardStub()
        }
        return true
    }

    companion object {
        private const val TAG = "GlassGestureListener"
    }
}
