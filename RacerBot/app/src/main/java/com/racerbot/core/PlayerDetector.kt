package com.racerbot.core

/**
 * Finds the car from its bright exhaust plume (always directly below the body), then walks up
 * through red body pixels to find the car's front edge.
 */
class PlayerDetector {
    fun detect(p: HsvPlanes, cfg: BotConfig): PlayerState? {
        val w = p.w
        val h = p.h
        val sc = w / 180f
        val y1 = (cfg.playerRegion.t * h).toInt()
        val y2 = (cfg.playerRegion.b * h).toInt().coerceAtMost(h)
        if (y2 <= y1) return null

        val col = FloatArray(w)
        for (y in y1 until y2) for (x in 0 until w) {
            val i = y * w + x
            if (Palette.isPlume(p.sat[i], p.value[i])) col[x] += 1f
        }
        // 7-tap box smoothing
        val sm = FloatArray(w)
        for (x in 0 until w) {
            var s = 0f
            for (k in -3..3) { val xx = x + k; if (xx in 0 until w) s += col[xx] }
            sm[x] = s / 7f
        }
        var px = 0
        var peak = 0f
        for (x in 0 until w) if (sm[x] > peak) { peak = sm[x]; px = x }
        if (peak < 6f * sc) return null

        val win = (14 * sc).toInt()
        val lo = (px - win).coerceAtLeast(0)
        val hi = (px + win + 1).coerceAtMost(w)
        var num = 0f
        var den = 0f
        for (x in lo until hi) { num += sm[x] * x; den += sm[x] }
        val cx = if (den > 0f) num / den else px.toFloat()

        val x0 = (cx - 15 * sc).toInt().coerceAtLeast(0)
        val x1 = (cx + 16 * sc).toInt().coerceAtMost(w)
        val minRed = (3 * sc).toInt().coerceAtLeast(2)
        var top = y1
        var y = y1 - 1
        var gap = 0
        val limit = (0.45f * h).toInt()
        while (y >= limit && gap <= 3) {
            var n = 0
            for (x in x0 until x1) { val i = y * w + x; if (Palette.isCarRed(p.hue[i], p.sat[i], p.value[i])) n++ }
            if (n >= minRed) { top = y; gap = 0 } else gap++
            y--
        }
        return PlayerState(cx, top.toFloat(), y1.toFloat(), (peak / (25f * sc)).coerceIn(0f, 1f))
    }
}
