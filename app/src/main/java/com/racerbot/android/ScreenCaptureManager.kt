package com.racerbot.android

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.racerbot.core.Frame

/**
 * MediaProjection capture straight into a small ImageReader (the system scales on the GPU), so we
 * never handle full-resolution frames. Frames are throttled and the latest one wins.
 */
class ScreenCaptureManager(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val onFrame: (Frame) -> Unit,
    private val onStopped: () -> Unit
) {
    private val thread = HandlerThread("racerbot-capture").also { it.start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var capW = 0
    private var capH = 0
    private var lastEmit = 0L
    private var started = false
    @Volatile var minFrameIntervalMs = 25L

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { onStopped() }
    }

    fun start(procWidth: Int) {
        if (!started) { projection.registerCallback(callback, handler); started = true }
        createDisplay(procWidth)
    }

    private fun createDisplay(procWidth: Int) {
        val (sw, sh) = ScreenInfo.realSize(ctx)
        capW = procWidth
        capH = Math.round(procWidth.toFloat() * sh / sw)
        val r = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ rd ->
            val img = try { rd.acquireLatestImage() } catch (e: Exception) { null }
            if (img != null) {
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastEmit >= minFrameIntervalMs) {
                        lastEmit = now
                        val plane = img.planes[0]
                        val buf = plane.buffer
                        val rowStride = plane.rowStride
                        val pixStride = plane.pixelStride
                        val out = IntArray(capW * capH)
                        for (y in 0 until capH) {
                            var idx = y * rowStride
                            val o = y * capW
                            for (x in 0 until capW) {
                                val rr = buf.get(idx).toInt() and 0xFF
                                val gg = buf.get(idx + 1).toInt() and 0xFF
                                val bb = buf.get(idx + 2).toInt() and 0xFF
                                out[o + x] = (rr shl 16) or (gg shl 8) or bb
                                idx += pixStride
                            }
                        }
                        onFrame(Frame(capW, capH, out, now))
                    }
                } finally {
                    img.close()
                }
            }
        }, handler)
        reader = r
        display = projection.createVirtualDisplay(
            "racerbot", capW, capH, ScreenInfo.densityDpi(ctx),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
        )
    }

    /** Called periodically: rebuilds the capture if the screen was rotated / resized. */
    fun restartIfGeometryChanged(procWidth: Int) {
        val (sw, sh) = ScreenInfo.realSize(ctx)
        val expectH = Math.round(procWidth.toFloat() * sh / sw)
        if (procWidth != capW || Math.abs(expectH - capH) > 2) {
            releaseDisplay()
            createDisplay(procWidth)
        }
    }

    private fun releaseDisplay() {
        try { display?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        display = null
        reader = null
    }

    fun stop() {
        releaseDisplay()
        try { projection.unregisterCallback(callback) } catch (e: Exception) { }
        try { projection.stop() } catch (e: Exception) { }
        thread.quitSafely()
    }
}
