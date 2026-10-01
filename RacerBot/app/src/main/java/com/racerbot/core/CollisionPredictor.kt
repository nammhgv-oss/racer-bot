package com.racerbot.core

class Threat(val track: Track, val ttc: Float, val overlapsCar: Boolean, val level: ThreatLevel)

/**
 * Time-to-collision from the measured ground-plane law of this game:
 * with u = y - horizon, du/dt = a * u^2  =>  TTC = (1/u - 1/uCar) / a.
 * The video gave a ~ 1.2 and horizon ~ 0.19 (fraction of screen height); `a` is re-learned online.
 */
class CollisionPredictor {
    var approachSpeed = 1.2f
        private set

    fun configure(cfg: BotConfig) { if (!learned) approachSpeed = cfg.approachSpeed }
    private var learned = false

    fun ttc(t: Track, carFront: Float, carBottom: Float, horizon: Float): Float {
        if (t.yTop > carBottom) return -1f           // already passed
        if (t.yBottom >= carFront) return 0f         // in contact band
        val u = (t.yBottom - horizon).coerceAtLeast(0.02f)
        val uc = (carFront - horizon).coerceAtLeast(0.05f)
        val a = approachSpeed.coerceAtLeast(0.3f)
        return ((1f / u) - (1f / uc)) / a
    }

    fun assess(tracks: List<Track>, carR: Float, cfg: BotConfig, carFront: Float, horizon: Float): List<Threat> {
        val carBottom = carFront + 0.09f
        val m = 0.03f
        val out = ArrayList<Threat>()
        for (t in tracks) {
            val ttc = ttc(t, carFront, carBottom, horizon)
            if (ttc < 0f || !t.moving(horizon, approachSpeed)) continue
            val overlap = (carR + cfg.carHalfWidth >= t.rL - m) && (carR - cfg.carHalfWidth <= t.rR + m)
            val level = if (t.kind != ObjectKind.OBSTACLE || !overlap) ThreatLevel.NONE else when {
                ttc < 0.5f -> ThreatLevel.CRITICAL
                ttc < 0.9f -> ThreatLevel.HIGH
                ttc < 1.5f -> ThreatLevel.MEDIUM
                else -> ThreatLevel.LOW
            }
            out.add(Threat(t, ttc, overlap, level))
        }
        return out
    }

    /** Refine the approach constant from tracked obstacles: a = vy / u^2. */
    fun learn(tracks: List<Track>, horizon: Float) {
        for (t in tracks) {
            if (t.kind != ObjectKind.OBSTACLE || t.hits < 5 || t.vy <= 0f) continue
            val u = t.yBottom - horizon
            if (u < 0.12f) continue
            val a = t.vy / (u * u)
            if (a in 0.3f..4f) { approachSpeed = 0.97f * approachSpeed + 0.03f * a; learned = true }
        }
    }
}
