package com.racerbot.core

class Decision(
    val targetIndex: Int, val targetR: Float, val currentIndex: Int, val currentR: Float,
    val direction: Int, val move: Boolean, val urgent: Boolean, val confidence: Float,
    val laneRisk: FloatArray, val laneEnergy: FloatArray, val positions: FloatArray,
    val overall: ThreatLevel, val reason: String
)

/**
 * Scores every planning position across the road, picks the best, and only commits to a move when
 * it is clearly better (hysteresis), the minimum stay time has passed and confidence is sufficient.
 */
class DecisionEngine {
    private var lastMoveMs = 0L
    private var lastDir = 0

    fun reset() { lastMoveMs = 0L; lastDir = 0 }
    fun notifyMoved(now: Long, dir: Int) { lastMoveMs = now; lastDir = dir }

    fun positions(cfg: BotConfig): FloatArray {
        val n = cfg.laneCount.coerceIn(2, 9)
        val margin = (cfg.carHalfWidth + 0.03f).coerceAtMost(0.45f)
        return FloatArray(n) { margin + it * (1f - 2f * margin) / (n - 1) }
    }

    private fun urgency(ttc: Float): Float {
        if (ttc <= 0f) return 1f
        val x = (1f - ttc / 2.2f).coerceIn(0f, 1f)
        return Math.pow(x.toDouble(), 0.7).toFloat()
    }

    fun decide(
        now: Long, carR: Float, threats: List<Threat>, energy: EnergyBarState,
        baseConf: Float, cfg: BotConfig
    ): Decision {
        val pos = positions(cfg)
        val n = pos.size
        val hw = cfg.carHalfWidth
        val lat = cfg.lateralSpeed.coerceAtLeast(0.2f)
        val m = 0.03f

        var kc = 0
        for (k in 1 until n) if (Math.abs(pos[k] - carR) < Math.abs(pos[kc] - carR)) kc = k

        val risk = FloatArray(n)
        val transit = FloatArray(n)
        val gain = FloatArray(n)
        var influence = 1f
        var overall = ThreatLevel.NONE

        for (t in threats) {
            val tr = t.track
            if (tr.kind == ObjectKind.OBSTACLE) {
                if (t.level.ordinal > overall.ordinal) overall = t.level
                val w = urgency(t.ttc) * tr.effConf
                if (w <= 0.02f) continue
                if (w > 0.15f && tr.effConf < influence) influence = tr.effConf
                val a = tr.rL - m
                val b = tr.rR + m
                for (k in 0 until n) {
                    if (pos[k] + hw >= a && pos[k] - hw <= b) risk[k] = 1f - (1f - risk[k]) * (1f - w)
                    if (k != kc) {
                        val lo = minOf(carR, pos[k]) - hw
                        val hi = maxOf(carR, pos[k]) + hw
                        val tt = Math.abs(pos[k] - carR) / lat + 0.15f
                        if (hi >= a && lo <= b && t.ttc < tt + 0.1f) transit[k] = maxOf(transit[k], 0.8f * tr.effConf)
                    }
                }
            } else if (t.ttc in 0.25f..2.0f) {
                val g = tr.effConf * Math.exp((-t.ttc / 1.3f).toDouble()).toFloat()
                val half = hw + 0.5f * (tr.rR - tr.rL)
                for (k in 0 until n) {
                    if (Math.abs(pos[k] - tr.rC) <= half) {
                        val reachable = k == kc || Math.abs(pos[k] - carR) / lat < t.ttc - 0.1f
                        if (reachable) gain[k] = minOf(1f, gain[k] + g)
                    }
                }
            }
        }

        val eff = FloatArray(n) { if (it == kc) risk[it] else maxOf(risk[it], transit[it]) }
        val boost = when {
            energy.present && energy.percent <= 0.35f -> 2.5f
            energy.present && energy.percent < 0.6f -> 1.5f
            else -> 1f
        }
        val score = FloatArray(n) { k ->
            -cfg.avoidPriority * 8f * eff[k] +
                cfg.energyPriority * 2f * boost * gain[k] * (1f - eff[k]) -
                0.25f * Math.abs(k - kc) +
                (if (k == kc) 0.35f else 0f) -
                0.15f * Math.abs(pos[k] - 0.5f)
        }
        var best = kc
        for (k in 0 until n) if (score[k] > score[best]) best = k

        val imminent = eff[kc] >= 0.6f
        var target = kc
        var reason = "giữ vị trí"
        if (best != kc) {
            val dir = if (best > kc) 1 else -1
            val since = now - lastMoveMs
            var need = 0.5f
            if (lastDir != 0 && dir != lastDir && since < 800L) need += 1.0f
            if (imminent && eff[kc] >= 0.85f) need = 0f
            val stayOk = since >= cfg.minLaneStayMs || imminent
            val adv = score[best] - score[kc]
            val betterRisk = eff[best] < eff[kc] - 0.25f
            if (stayOk && (adv >= need || (imminent && betterRisk))) {
                target = best
                reason = if (imminent) "né chướng ngại" else "làn tốt hơn / lấy năng lượng"
            } else if (!stayOk) reason = "chờ thời gian tối thiểu"
            else reason = "chưa đủ lợi thế"
        }

        var conf = (baseConf * (0.5f + 0.5f * influence)).coerceIn(0f, 1f)
        var move = target != kc
        if (move && conf < cfg.confidenceThreshold) { move = false; reason = "độ tin cậy thấp — chờ thêm khung hình" }
        val dir = if (!move) 0 else if (target > kc) 1 else -1
        return Decision(target, pos[target], kc, carR, dir, move, imminent, conf, eff, gain, pos, overall, reason)
    }
}
