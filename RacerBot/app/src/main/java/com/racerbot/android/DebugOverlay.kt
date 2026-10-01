package com.racerbot.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.racerbot.core.*

/**
 * Two modes:
 *  - MINI (default): a small radar + text window in the bottom-left corner, outside the analysed region,
 *    so it cannot disturb detection (MediaProjection also captures our own overlays!).
 *  - FULL: boxes/lane lines drawn over the whole game. Used for calibration (no input is sent then);
 *    during RUNNING it may pollute detection, so it is opt-in.
 * Colours: GREEN safe, RED dangerous, YELLOW collectible, BLUE player, WHITE lane boundaries.
 */
class DebugOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: OverlayView? = null
    private var full = false
    @Volatile private var snap: OverlaySnapshot? = null

    fun isShown() = view != null
    fun isFull() = full

    /** Normalised rect occupied by the MINI window (so detectors can ignore it). */
    fun bounds(): NRect? = if (view != null && !full) NRect(0f, 0.80f, 0.5f, 1f) else null

    fun show(fullMode: Boolean) {
        if (view != null && full == fullMode) return
        hide()
        full = fullMode
        val (sw, sh) = ScreenInfo.realSize(ctx)
        val v = OverlayView(ctx, fullMode)
        val lp = if (fullMode) WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FLAGS, PixelFormat.TRANSLUCENT
        ).also { it.gravity = Gravity.TOP or Gravity.START }
        else WindowManager.LayoutParams(
            (sw * 0.5f).toInt(), (sh * 0.20f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FLAGS, PixelFormat.TRANSLUCENT
        ).also { it.gravity = Gravity.BOTTOM or Gravity.START }
        try { wm.addView(v, lp); view = v } catch (e: Exception) { view = null }
    }

    fun hide() {
        view?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        view = null
    }

    fun update(s: OverlaySnapshot) { snap = s; view?.postInvalidate() }

    private inner class OverlayView(c: Context, private val fullMode: Boolean) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; isAntiAlias = false }
        private val fill = Paint().apply { style = Paint.Style.FILL }
        private val text = Paint().apply { color = Color.WHITE; textSize = 10f * d; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }

        override fun onDraw(canvas: Canvas) {
            val s = snap ?: return
            if (fullMode) drawFull(canvas, s) else drawMini(canvas, s)
        }

        private fun laneCell(dec: Decision, k: Int): Pair<Float, Float> {
            val n = dec.positions.size
            val half = if (n > 1) (dec.positions[1] - dec.positions[0]) / 2f else 0.5f
            return Pair((dec.positions[k] - half).coerceAtLeast(0f), (dec.positions[k] + half).coerceAtMost(1f))
        }

        private fun worst(s: OverlaySnapshot): Threat? =
            s.threats.filter { it.level != ThreatLevel.NONE }.minByOrNull { it.ttc }

        private fun drawMini(c: Canvas, s: OverlaySnapshot) {
            fill.color = Color.argb(185, 0, 0, 0)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            val pad = 6f * d
            val rx = pad
            val ry = pad
            val rw = width * 0.40f
            val rh = height - 2 * pad
            val dec = s.decision
            val horizon = s.cfg.perspectiveHorizonY
            val carFront = s.vr.player?.let { it.top / s.vr.height } ?: s.cfg.carFrontY
            if (dec != null) {
                for (k in dec.positions.indices) {
                    val (a, b) = laneCell(dec, k)
                    fill.color = if (dec.laneRisk[k] >= 0.35f) Color.argb(110, 255, 23, 68) else Color.argb(90, 0, 230, 118)
                    c.drawRect(rx + a * rw, ry, rx + b * rw, ry + rh, fill)
                    stroke.color = Color.WHITE; stroke.strokeWidth = 1f * d
                    c.drawLine(rx + a * rw, ry, rx + a * rw, ry + rh, stroke)
                    if (k == dec.targetIndex) {
                        stroke.color = Color.WHITE; stroke.strokeWidth = 2f * d
                        c.drawRect(rx + a * rw, ry, rx + b * rw, ry + rh, stroke)
                    }
                }
            } else {
                stroke.color = Color.WHITE; stroke.strokeWidth = 1f * d
                c.drawRect(rx, ry, rx + rw, ry + rh, stroke)
            }
            for (t in s.tracks) {
                val py = ry + rh * ((t.yBottom - horizon) / (carFront - horizon)).coerceIn(0f, 1f)
                fill.color = if (t.kind == ObjectKind.OBSTACLE) Color.rgb(255, 23, 68) else Color.rgb(255, 234, 0)
                c.drawRect(rx + t.rL.coerceIn(0f, 1f) * rw, py - 4f * d, rx + t.rR.coerceIn(0f, 1f) * rw, py, fill)
            }
            if (dec != null) {
                fill.color = Color.rgb(41, 121, 255)
                val cx = rx + dec.currentR.coerceIn(0f, 1f) * rw
                val hw = s.cfg.carHalfWidth * rw
                c.drawRect(cx - hw, ry + rh - 12f * d, cx + hw, ry + rh, fill)
            }
            // text
            val tx = rx + rw + pad
            var ty = ry + 10f * d
            val lh = 12f * d
            fun line(t: String) { c.drawText(t, tx, ty, text); ty += lh }
            val w = worst(s)
            line("PLAYER: ${s.status.laneName}")
            line("STATE: ${s.status.gameState}")
            line("OBST ${s.status.obstacleCount}  EN ${s.status.energyCount}")
            line(if (w != null) "RISK: ${w.level} TTC ${"%.2f".format(w.ttc)}s" else "RISK: NONE")
            line("TARGET: " + (dec?.let { (it.targetIndex + 1).toString() + "/" + it.positions.size } ?: "-"))
            line("ACT: ${s.status.lastAction}")
            line("FPS ${"%.0f".format(s.status.fps)} CONF ${"%.0f".format((dec?.confidence ?: 0f) * 100)}%")
            line((dec?.reason ?: "").take(26))
        }

        private fun drawFull(c: Canvas, s: OverlaySnapshot) {
            val vr = s.vr
            val sx = width.toFloat() / vr.width
            val sy = height.toFloat() / vr.height
            val lanes = vr.lanes
            val n = s.decision?.positions?.size ?: s.cfg.laneCount
            val y0 = (s.cfg.gameRegion.t * vr.height).toInt()
            val y1 = (s.cfg.gameRegion.b * vr.height).toInt().coerceAtMost(vr.height)
            stroke.color = Color.WHITE; stroke.strokeWidth = 1.5f * d
            // road boundaries + lane dividers
            for (k in 0..n) {
                val r = k.toFloat() / n
                var px = -1f
                var py = -1f
                var y = y0
                while (y < y1) {
                    if (lanes.valid(y)) {
                        val x = lanes.toScreen(r, y) * sx
                        val yy = y * sy
                        if (px >= 0f) c.drawLine(px, py, x, yy, stroke)
                        px = x; py = yy
                    }
                    y += 6
                }
            }
            // energy bar region (calibration)
            if (s.status.mode == BotMode.CALIBRATING) {
                val r = s.cfg.energyBarRegion
                stroke.color = Color.rgb(255, 234, 0)
                c.drawRect(r.l * width, r.t * height, r.r * width, r.b * height, stroke)
                val g = s.cfg.gameRegion
                stroke.color = Color.WHITE
                c.drawRect(g.l * width, g.t * height, g.r * width, g.b * height, stroke)
                val p = s.cfg.playerRegion
                stroke.color = Color.rgb(41, 121, 255)
                c.drawRect(p.l * width, p.t * height, p.r * width, p.b * height, stroke)
            }
            for (det in vr.obstacles) {
                val dec = s.decision
                val b = det.blob
                stroke.color = Color.rgb(255, 23, 68)
                c.drawRect(b.x * sx, b.y * sy, b.right * sx, b.bottom * sy, stroke)
                c.drawText("${(det.confidence * 100).toInt()}%", b.x * sx, b.y * sy - 2f, text)
            }
            for (det in vr.energies) {
                val b = det.blob
                stroke.color = Color.rgb(255, 234, 0)
                c.drawRect(b.x * sx, b.y * sy, b.right * sx, b.bottom * sy, stroke)
                c.drawText("${(det.confidence * 100).toInt()}%", b.x * sx, b.y * sy - 2f, text)
            }
            vr.player?.let { p ->
                stroke.color = Color.rgb(41, 121, 255)
                c.drawRect((p.cx - 16f * vr.width / 180f) * sx, p.top * sy, (p.cx + 16f * vr.width / 180f) * sx, p.bottom * sy, stroke)
            }
            s.decision?.let { dec ->
                val row = (vr.player?.bottom?.toInt() ?: (0.62f * vr.height).toInt()).coerceIn(0, vr.height - 1) - 1
                for (k in dec.positions.indices) {
                    val x = lanes.toScreen(dec.positions[k], row.coerceAtLeast(0)) * sx
                    stroke.color = if (dec.laneRisk[k] >= 0.35f) Color.rgb(255, 23, 68) else Color.rgb(0, 230, 118)
                    c.drawCircle(x, (row.coerceAtLeast(0)) * sy + 40f * d, if (k == dec.targetIndex) 9f * d else 5f * d, stroke)
                }
                c.drawText("TARGET ${dec.targetIndex + 1}/${dec.positions.size}  ${dec.reason}", 8f * d, 90f * d, text)
            }
            c.drawText("${s.status.gameState}  FPS ${"%.0f".format(s.status.fps)}  ${s.status.lastAction}", 8f * d, 76f * d, text)
        }
    }

    companion object {
        private const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
