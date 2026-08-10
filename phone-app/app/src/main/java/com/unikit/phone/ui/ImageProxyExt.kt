package com.unikit.phone.ui

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a YUV_420_888 CameraX frame to a JPEG byte array via NV21.
 * Reads each plane's own rowStride/pixelStride rather than concatenating
 * the raw buffers -- some devices give the U/V planes pixelStride=2
 * (already interleaved), others pixelStride=1 (fully planar), and the
 * naive shortcut only works for the former.
 *
 * Sensor-orientation only, no rotation correction yet -- see
 * docs/glass_app.md "Known limitations"; today's goal is a frame landing
 * on Glass at all.
 */
fun ImageProxy.toJpegBytes(quality: Int): ByteArray? {
    if (format != ImageFormat.YUV_420_888) return null

    val nv21 = ByteArray(width * height * 3 / 2)
    var pos = 0

    val yPlane = planes[0]
    val yBuffer = yPlane.buffer
    for (row in 0 until height) {
        yBuffer.position(row * yPlane.rowStride)
        yBuffer.get(nv21, pos, width)
        pos += width
    }

    val uPlane = planes[1]
    val vPlane = planes[2]
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
            val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            nv21[pos++] = vBuffer.get(vIndex)
            nv21[pos++] = uBuffer.get(uIndex)
        }
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
    return out.toByteArray()
}
