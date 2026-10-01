package com.racerbot.android

import android.content.Context
import android.content.SharedPreferences
import com.racerbot.core.BotConfig
import com.racerbot.core.ControlMode
import com.racerbot.core.NRect

/** Local-only persistence (SharedPreferences). Also stores the calibration profile. */
class SettingsRepository private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("racerbot_cfg", Context.MODE_PRIVATE)

    @Volatile
    var current: BotConfig = load()
        private set

    fun update(f: (BotConfig) -> BotConfig) {
        synchronized(this) { current = f(current); save(current) }
    }

    fun reset() {
        synchronized(this) { sp.edit().clear().apply(); current = BotConfig() }
    }

    private fun rect(k: String, d: NRect) = NRect(
        sp.getFloat(k + "_l", d.l), sp.getFloat(k + "_t", d.t),
        sp.getFloat(k + "_r", d.r), sp.getFloat(k + "_b", d.b)
    )

    private fun SharedPreferences.Editor.putRect(k: String, r: NRect): SharedPreferences.Editor =
        putFloat(k + "_l", r.l).putFloat(k + "_t", r.t).putFloat(k + "_r", r.r).putFloat(k + "_b", r.b)

    private fun load(): BotConfig {
        val d = BotConfig()
        val mode = try { ControlMode.valueOf(sp.getString("controlMode", d.controlMode.name) ?: d.controlMode.name) }
        catch (e: Exception) { d.controlMode }
        return BotConfig(
            autoRestart = sp.getBoolean("autoRestart", d.autoRestart),
            debugOverlay = sp.getBoolean("debugOverlay", d.debugOverlay),
            debugFull = sp.getBoolean("debugFull", d.debugFull),
            obstacleSensitivity = sp.getFloat("obstacleSensitivity", d.obstacleSensitivity),
            energySensitivity = sp.getFloat("energySensitivity", d.energySensitivity),
            energyPriority = sp.getFloat("energyPriority", d.energyPriority),
            avoidPriority = sp.getFloat("avoidPriority", d.avoidPriority),
            controlMode = mode,
            swipeDistance = sp.getFloat("swipeDistance", d.swipeDistance),
            swipeDurationMs = sp.getInt("swipeDurationMs", d.swipeDurationMs),
            actionCooldownMs = sp.getInt("actionCooldownMs", d.actionCooldownMs),
            decisionIntervalMs = sp.getInt("decisionIntervalMs", d.decisionIntervalMs),
            confidenceThreshold = sp.getFloat("confidenceThreshold", d.confidenceThreshold),
            minLaneStayMs = sp.getInt("minLaneStayMs", d.minLaneStayMs),
            dragGain = sp.getFloat("dragGain", d.dragGain),
            gestureAnchorY = sp.getFloat("gestureAnchorY", d.gestureAnchorY),
            invertSwipe = sp.getBoolean("invertSwipe", d.invertSwipe),
            laneCount = sp.getInt("laneCount", d.laneCount),
            procWidth = sp.getInt("procWidth", d.procWidth),
            gameRegion = rect("gameRegion", d.gameRegion),
            energyBarRegion = rect("energyBarRegion", d.energyBarRegion),
            energySegments = sp.getInt("energySegments", d.energySegments),
            playerRegion = rect("playerRegion", d.playerRegion),
            carHalfWidth = sp.getFloat("carHalfWidth", d.carHalfWidth),
            carFrontY = sp.getFloat("carFrontY", d.carFrontY),
            perspectiveHorizonY = sp.getFloat("perspectiveHorizonY", d.perspectiveHorizonY),
            approachSpeed = sp.getFloat("approachSpeed", d.approachSpeed),
            lateralSpeed = sp.getFloat("lateralSpeed", d.lateralSpeed),
            securityScan = sp.getBoolean("securityScan", d.securityScan),
            targetPackage = sp.getString("targetPackage", d.targetPackage) ?: "",
            restartTapX = sp.getFloat("restartTapX", d.restartTapX),
            restartTapY = sp.getFloat("restartTapY", d.restartTapY),
            startGraceMs = sp.getInt("startGraceMs", d.startGraceMs)
        )
    }

    private fun save(c: BotConfig) {
        sp.edit()
            .putBoolean("autoRestart", c.autoRestart)
            .putBoolean("debugOverlay", c.debugOverlay)
            .putBoolean("debugFull", c.debugFull)
            .putFloat("obstacleSensitivity", c.obstacleSensitivity)
            .putFloat("energySensitivity", c.energySensitivity)
            .putFloat("energyPriority", c.energyPriority)
            .putFloat("avoidPriority", c.avoidPriority)
            .putString("controlMode", c.controlMode.name)
            .putFloat("swipeDistance", c.swipeDistance)
            .putInt("swipeDurationMs", c.swipeDurationMs)
            .putInt("actionCooldownMs", c.actionCooldownMs)
            .putInt("decisionIntervalMs", c.decisionIntervalMs)
            .putFloat("confidenceThreshold", c.confidenceThreshold)
            .putInt("minLaneStayMs", c.minLaneStayMs)
            .putFloat("dragGain", c.dragGain)
            .putFloat("gestureAnchorY", c.gestureAnchorY)
            .putBoolean("invertSwipe", c.invertSwipe)
            .putInt("laneCount", c.laneCount)
            .putInt("procWidth", c.procWidth)
            .putRect("gameRegion", c.gameRegion)
            .putRect("energyBarRegion", c.energyBarRegion)
            .putInt("energySegments", c.energySegments)
            .putRect("playerRegion", c.playerRegion)
            .putFloat("carHalfWidth", c.carHalfWidth)
            .putFloat("carFrontY", c.carFrontY)
            .putFloat("perspectiveHorizonY", c.perspectiveHorizonY)
            .putFloat("approachSpeed", c.approachSpeed)
            .putFloat("lateralSpeed", c.lateralSpeed)
            .putBoolean("securityScan", c.securityScan)
            .putString("targetPackage", c.targetPackage)
            .putFloat("restartTapX", c.restartTapX)
            .putFloat("restartTapY", c.restartTapY)
            .putInt("startGraceMs", c.startGraceMs)
            .apply()
    }

    companion object {
        @Volatile private var inst: SettingsRepository? = null
        fun get(ctx: Context): SettingsRepository =
            inst ?: synchronized(this) {
                inst ?: SettingsRepository(ctx.applicationContext).also { inst = it }
            }
    }
}
