package com.racerbot.android

import android.content.Context
import com.racerbot.core.BotConfig
import com.racerbot.core.ControlMode

/**
 * One gesture at a time, with cooldown. In PROPORTIONAL_DRAG mode the swipe length is
 * gain * (needed car shift); the gain is learned from how far the car really moved afterwards
 * (closed loop), because the video alone cannot reveal the finger-to-car mapping.
 */
class SwipeController(private val ctx: Context, private val repo: SettingsRepository) {
    @Volatile private var busy = false
    var lastActionMs = 0L
        private set
    var lastActionText = "—"
        private set
    var hint = ""
        private set

    private class Pending(val carX: Float, val fingerPx: Float, val dirCar: Int, val t: Long)
    private var pending: Pending? = null
    private var oppositeCount = 0

    fun canAct(now: Long, cfg: BotConfig): Boolean =
        !busy && now - lastActionMs >= cfg.actionCooldownMs && BotAccessibilityService.instance != null

    /** dxCarPx > 0 means the car must go right (screen px). Returns true if a gesture was sent. */
    fun move(dxCarPx: Float, carXScreen: Float, cfg: BotConfig, now: Long): Boolean {
        val svc = BotAccessibilityService.instance ?: return false
        val (sw, sh) = ScreenInfo.realSize(ctx)
        val dirCar = if (dxCarPx >= 0f) 1 else -1
        val finger = if (cfg.invertSwipe) -dirCar else dirCar
        val dist = if (cfg.controlMode == ControlMode.DISCRETE_SWIPE) cfg.swipeDistance * sw
        else (Math.abs(dxCarPx) * cfg.dragGain).coerceIn(0.04f * sw, 0.45f * sw)
        val y = cfg.gestureAnchorY * sh
        val x0 = sw / 2f - finger * dist / 2f
        val x1 = sw / 2f + finger * dist / 2f
        busy = true
        val ok = svc.swipe(x0, y, x1, y, cfg.swipeDurationMs.toLong()) { busy = false }
        if (!ok) { busy = false; return false }
        lastActionMs = now
        lastActionText = (if (dirCar > 0) "VUỐT PHẢI " else "VUỐT TRÁI ") + dist.toInt() + "px"
        pending = Pending(carXScreen, dist, dirCar, now)
        return true
    }

    /** Compare the car position after a swipe to what was expected, and adapt the gain. */
    fun observe(carXScreen: Float, cfg: BotConfig, now: Long) {
        val p = pending ?: return
        if (now - p.t < cfg.swipeDurationMs + 300L) return
        pending = null
        val moved = (carXScreen - p.carX) * p.dirCar
        if (moved > 8f) {
            oppositeCount = 0
            hint = ""
            if (cfg.controlMode == ControlMode.PROPORTIONAL_DRAG) {
                val sample = p.fingerPx / moved
                val g = (0.7f * cfg.dragGain + 0.3f * sample).coerceIn(0.3f, 8f)
                repo.update { it.copy(dragGain = g) }
            }
        } else if (moved < -8f) {
            oppositeCount++
            if (oppositeCount >= 3) hint = "Xe đi ngược hướng vuốt — thử bật 'Đảo hướng vuốt' trong Cài đặt"
        }
    }

    fun tap(xNorm: Float, yNorm: Float): Boolean {
        val svc = BotAccessibilityService.instance ?: return false
        val (sw, sh) = ScreenInfo.realSize(ctx)
        return svc.tap(xNorm * sw, yNorm * sh)
    }
}
