package com.racerbot.core

/**
 * Energy pickups (yellow capsule / red can) are found by colour key and verified by checking that
 * navy road surrounds them on both sides (rejects the red/white rails). Also reads the HUD bar.
 */
class EnergyDetector {

    fun detectCollectibles(
        p: HsvPlanes, finder: BlobFinder, cfg: BotConfig, player: PlayerState?, lanes: LaneModel,
        ignored: (Blob) -> Boolean
    ): List<Detection> {
        val w = p.w
        val h = p.h
        val sc = w / 180f
        val y0 = (cfg.gameRegion.t * h).toInt()
        val y1 = (cfg.gameRegion.b * h).toInt().coerceAtMost(h)
        val m = finder.collectMask
        java.util.Arrays.fill(m, 0.toByte())
        for (y in y0 until y1) for (x in 0 until w) {
            val i = y * w + x
            val hh = p.hue[i]; val ss = p.sat[i]; val vv = p.value[i]
            if (Palette.isCollectYellow(hh, ss, vv) || Palette.isCollectRed(hh, ss, vv)) m[i] = 1
        }
        finder.close3x3(m, y0, y1)
        val minArea = (6 * sc * sc).toInt().coerceAtLeast(4)
        val ringMin = 0.40f - 0.30f * cfg.energySensitivity
        val o1 = (2 * sc).toInt().coerceAtLeast(1)
        val o2 = (5 * sc).toInt().coerceAtLeast(o1 + 1)
        val out = ArrayList<Detection>()
        for (b in finder.components(m, p, y0, y1, minArea)) {
            val cy = (b.y + b.h / 2f) / h
            if (b.w > (6 + cy * 14) * sc) continue
            if (b.h.toFloat() / b.w < 1.1f) continue
            if (b.solidity < 0.5f) continue
            if (player != null && b.bottom > player.top + 4 * sc &&
                b.x < player.cx + 16 * sc && b.right > player.cx - 16 * sc) continue
            if (ignored(b)) continue
            val ya = (b.y - 1).coerceAtLeast(0)
            val yb = (b.bottom + 1).coerceAtMost(h)
            val lf = roadFraction(p, b.x - o2, b.x - o1, ya, yb)
            val rt = roadFraction(p, b.right + o1, b.right + o2, ya, yb)
            val tp = roadFraction(p, b.x, b.right, (b.y - o2).coerceAtLeast(0), (b.y - o1).coerceAtLeast(0))
            if (lf < ringMin || rt < ringMin || (lf + rt + tp) / 3f < ringMin + 0.10f) continue
            val rl = lanes.toRoad(b.x.toFloat(), b.bottom - 1)
            val rr = lanes.toRoad(b.right.toFloat(), b.bottom - 1)
            val conf = (0.45f + 0.35f * ((lf + rt) / 2f) + 0.2f * (b.h.toFloat() / b.w / 2.5f).coerceAtMost(1f)).coerceIn(0f, 1f)
            out.add(Detection(ObjectKind.ENERGY, b, rl, rr, b.y.toFloat() / h, b.bottom.toFloat() / h, conf))
        }
        return out
    }

    private fun roadFraction(p: HsvPlanes, xa0: Int, xb0: Int, ya: Int, yb: Int): Float {
        val xa = xa0.coerceAtLeast(0)
        val xb = xb0.coerceAtMost(p.w)
        if (xb <= xa || yb <= ya) return 0f
        var n = 0
        var t = 0
        for (y in ya until yb) for (x in xa until xb) {
            val i = y * p.w + x
            if (Palette.isRoad(p.hue[i], p.sat[i], p.value[i])) n++
            t++
        }
        return if (t == 0) 0f else n.toFloat() / t
    }

    /** Reads the HUD energy bar: segment is LIT (saturated warm) / EMPTY (grey) / unknown. */
    fun readBar(p: HsvPlanes, cfg: BotConfig): EnergyBarState {
        val n = cfg.energySegments.coerceAtLeast(1)
        val r = cfg.energyBarRegion
        val y = (((r.t + r.b) / 2f) * p.h).toInt().coerceIn(1, p.h - 2)
        var lit = 0
        var recognised = 0
        for (i in 0 until n) {
            val cx = ((r.l + (i + 0.5f) * (r.r - r.l) / n) * p.w).toInt()
            var sSum = 0
            var vSum = 0
            var cnt = 0
            for (dy in -1..1) for (dx in -2..2) {
                val xx = cx + dx
                if (xx < 0 || xx >= p.w) continue
                val idx = (y + dy) * p.w + xx
                sSum += p.sat[idx]; vSum += p.value[idx]; cnt++
            }
            if (cnt == 0) continue
            val s = sSum / cnt
            val v = vSum / cnt
            if (s >= 90 && v >= 120) { lit++; recognised++ }
            else if (s <= 45 && v in 35..150) recognised++
        }
        return EnergyBarState(n, lit, recognised)
    }
}
