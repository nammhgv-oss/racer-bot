package com.racerbot.core

/**
 * State machine driven only by what is visible. The reference video contains gameplay only, so
 * MAIN_MENU / GAME_OVER / RESTART_SCREEN are INFERRED (HUD vanished after gameplay) and were not
 * verified against real screens. Any non-PLAYING state means: no input is sent.
 */
class GameStateDetector {
    var state = GameState.UNKNOWN
        private set
    private var hudSince = -1L
    private var noHudSince = -1L
    private var lastPlaying = -1L
    private var staticSince = -1L

    fun update(v: VisionResult, security: Boolean, now: Long, cfg: BotConfig): GameState {
        if (security) { state = GameState.SECURITY_CHALLENGE; return state }
        val hud = v.energyBar.present
        val road = v.lanes.roadFractionLower > 0.22f && v.lanes.confidence > 0.3f
        if (hud && road) {
            noHudSince = -1L
            if (hudSince < 0) hudSince = now
            if (v.motion < 0.15f) { if (staticSince < 0) staticSince = now } else staticSince = -1L
            state = when {
                staticSince >= 0 && now - staticSince > 1500L -> GameState.PAUSED
                now - hudSince < cfg.startGraceMs -> GameState.STARTING
                else -> GameState.PLAYING
            }
            if (state == GameState.PLAYING) lastPlaying = now
        } else {
            hudSince = -1L
            staticSince = -1L
            if (noHudSince < 0) noHudSince = now
            val dt = now - noHudSince
            state = when {
                v.meanBrightness < 14f -> GameState.LOADING
                dt < 1200L -> GameState.UNKNOWN
                lastPlaying > 0 && now - lastPlaying < 60_000L -> GameState.GAME_OVER
                else -> GameState.MAIN_MENU
            }
        }
        return state
    }
}
