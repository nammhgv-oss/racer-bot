package com.racerbot.android

import com.racerbot.core.NRect
import com.racerbot.core.VisionResult

/** Calibration helpers. Every change is persisted immediately by SettingsRepository (the "profile"). */
class CalibrationManager(private val repo: SettingsRepository) {
    private var carTopEma = -1f
    private var samples = 0

    fun observe(vr: VisionResult) {
        val p = vr.player ?: return
        val t = p.top / vr.height
        carTopEma = if (carTopEma < 0f) t else 0.95f * carTopEma + 0.05f * t
        samples++
    }

    /** Stores the measured car-front position; needs ~1 s of stable player detection. */
    fun applyAuto(): Boolean {
        if (samples < 30 || carTopEma < 0f) return false
        repo.update { it.copy(carFrontY = carTopEma.coerceIn(0.45f, 0.70f)) }
        return true
    }

    fun setGameRegion(r: NRect) = repo.update { it.copy(gameRegion = r) }
    fun setEnergyBarRegion(r: NRect) = repo.update { it.copy(energyBarRegion = r) }
    fun setPlayerRegion(r: NRect) = repo.update { it.copy(playerRegion = r) }
    fun setRestartTap(x: Float, y: Float) = repo.update { it.copy(restartTapX = x, restartTapY = y) }
}
