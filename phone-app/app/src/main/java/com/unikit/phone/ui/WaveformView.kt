package com.unikit.phone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Minimal Canvas line-plot of the last N samples of a raw_signal stream
 * (ECG / PPG_RED / PPG_IR). No charting library is used -- none is cached
 * in ~/.gradle (see plan #1) and a simple scrolling line is all these
 * screens need.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#FF0D6E8C")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val emptyPaint = Paint().apply {
        color = Color.parseColor("#FF9AA5AF")
        textSize = 32f
        isAntiAlias = true
    }

    private var samples: List<Float> = emptyList()

    /** Replaces the whole buffer -- used when a fragment resets on entry. */
    fun setSamples(newSamples: List<Float>) {
        samples = newSamples
        invalidate()
    }

    /** Appends a chunk, keeping only the most recent [maxSamples]. */
    fun pushSamples(chunk: List<Float>, maxSamples: Int = 400) {
        samples = (samples + chunk).takeLast(maxSamples)
        invalidate()
    }

    fun clear() {
        samples = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.size < 2) {
            canvas.drawText("NO SIGNAL", 24f, height / 2f, emptyPaint)
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val minV = samples.min()
        val maxV = samples.max()
        val range = (maxV - minV).let { if (it > 0.0001f) it else 1f }
        val stepX = if (samples.size > 1) w / (samples.size - 1) else w

        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val normalized = (v - minV) / range
            val y = h - (normalized * h * 0.8f) - (h * 0.1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
