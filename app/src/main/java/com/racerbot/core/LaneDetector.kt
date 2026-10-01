package com.racerbot.core

/**
 * Builds the road model: per-row drivable extent following the navy run that overlaps the row
 * below (handles curves/forks). Lane dashes in the game are decorative; the car steers
 * continuously, so "lanes" are virtual positions evenly spread across the road.
 */
class LaneDetector {
    fun detect(p: HsvPlanes, cfg: BotConfig, seedX: Int): LaneModel {
        val w = p.w
        val h = p.h
        val sc = w / 180f
        val y0 = (cfg.gameRegion.t * h).toInt()
        val y1 = (cfg.gameRegion.b * h).toInt().coerceAtMost(h)
        val extL = IntArray(h) { -1 }
        val extR = IntArray(h) { -1 }
        var curL = seedX
        var curR = seedX
        var miss = 0
        val minRun = (6 * sc).toInt().coerceAtLeast(3)
        val accept = -(w * 0.10f).toInt()
        var roadPix = 0
        var lowerPix = 0
        val lowerStart = (0.55f * h).toInt()

        var y = y1 - 1
        while (y >= y0) {
            val frac = (y - y0).toFloat() / (y1 - y0).coerceAtLeast(1)
            val gap = (w * (0.05f + 0.30f * frac)).toInt()
            var bs = -1
            var be = -1
            var bo = Int.MIN_VALUE
            var runStart = -1
            var last = -1
            val base = y * w
            for (x in 0 until w) {
                val i = base + x
                if (Palette.isRoad(p.hue[i], p.sat[i], p.value[i])) {
                    roadPix++
                    if (y >= lowerStart) lowerPix++
                    if (runStart < 0) runStart = x
                    else if (x - last > gap) {
                        if (last - runStart >= minRun) {
                            val ov = minOf(last, curR) - maxOf(runStart, curL)
                            if (ov > bo) { bo = ov; bs = runStart; be = last }
                        }
                        runStart = x
                    }
                    last = x
                }
            }
            if (runStart >= 0 && last - runStart >= minRun) {
                val ov = minOf(last, curR) - maxOf(runStart, curL)
                if (ov > bo) { bo = ov; bs = runStart; be = last }
            }
            if (bs >= 0 && bo >= accept) {
                extL[y] = bs; extR[y] = be; curL = bs; curR = be; miss = 0
            } else {
                miss++
                if (miss > 6) break
            }
            y--
        }

        // widen over +-4 rows so glow / objects that split the road do not shrink it
        val k = (4 * sc).toInt().coerceAtLeast(1)
        val wl = extL.copyOf()
        val wr = extR.copyOf()
        for (r in y0 until y1) {
            if (extL[r] < 0) continue
            var mn = extL[r]
            var mx = extR[r]
            for (q in maxOf(y0, r - k)..minOf(y1 - 1, r + k)) {
                if (extL[q] >= 0) { if (extL[q] < mn) mn = extL[q]; if (extR[q] > mx) mx = extR[q] }
            }
            wl[r] = mn; wr[r] = mx
        }

        var horizon = -1
        for (r in y0 until y1) if (wl[r] >= 0) { horizon = r; break }
        var good = 0
        var total = 0
        for (r in (0.45f * h).toInt() until y1) { total++; if (wl[r] >= 0) good++ }
        val lowerArea = ((y1 - lowerStart) * w).coerceAtLeast(1)
        return LaneModel(
            w, h, wl, wr, horizon,
            lowerPix.toFloat() / lowerArea,
            if (total == 0) 0f else good.toFloat() / total
        )
    }
}
