package com.racerbot.core

data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun contains(x: Float, y: Float): Boolean = x in l..r && y in t..b
}

/**
 * All values that could not be determined reliably from the reference video are configurable.
 * Coordinates are normalised (0..1) so the profile works on any screen resolution.
 */
data class BotConfig(
    val autoRestart: Boolean = false,
    val debugOverlay: Boolean = true,
    /** false = small radar window in a corner (safe for detection); true = boxes over the game. */
    val debugFull: Boolean = false,
    val obstacleSensitivity: Float = 0.5f,
    val energySensitivity: Float = 0.5f,
    val energyPriority: Float = 0.5f,
    val avoidPriority: Float = 0.8f,

    // --- input ---
    val controlMode: ControlMode = ControlMode.PROPORTIONAL_DRAG,
    val swipeDistance: Float = 0.18f,      // fraction of screen width (DISCRETE_SWIPE)
    val swipeDurationMs: Int = 110,
    val actionCooldownMs: Int = 350,
    val decisionIntervalMs: Int = 40,
    val confidenceThreshold: Float = 0.45f,
    val minLaneStayMs: Int = 350,
    val dragGain: Float = 1.0f,            // finger px per car px, learned online
    val gestureAnchorY: Float = 0.88f,
    val invertSwipe: Boolean = false,

    // --- geometry (measured from the video unless noted) ---
    val laneCount: Int = 5,                // planning positions across the road (car steers continuously)
    val procWidth: Int = 180,
    val gameRegion: NRect = NRect(0f, 0.05f, 1f, 0.78f),
    val energyBarRegion: NRect = NRect(0.254f, 0.108f, 0.746f, 0.140f),
    val energySegments: Int = 7,
    val playerRegion: NRect = NRect(0f, 0.62f, 1f, 0.76f),
    val carHalfWidth: Float = 0.12f,       // road-relative (car ~0.25 of road width)
    val carFrontY: Float = 0.58f,          // fallback when the player is not found
    val perspectiveHorizonY: Float = 0.19f,// fitted from the video: vy ~ 1.2*(y-0.19)^2 H/s
    val approachSpeed: Float = 1.2f,
    val lateralSpeed: Float = 0.6f,        // road widths per second (rough, learned online)

    // --- safety / state ---
    val securityScan: Boolean = true,
    val targetPackage: String = "",
    val restartTapX: Float = -1f,
    val restartTapY: Float = -1f,
    val startGraceMs: Int = 900
)
