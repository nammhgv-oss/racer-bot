package com.racerbot.core

/** Anything non-road, non-paint inside the drivable extent that is not an energy pickup. */
class ObstacleDetector {

    fun detect(
        p: HsvPlanes, finder: BlobFinder, cfg: BotConfig, player: PlayerState?, lanes: LaneModel,
        energies: List<Detection>, ignored: (Blob) -> Boolean
    ): List<Detection> {
        val w = p.w
        val h = p.h
        val sc = w / 180f
        val y0 = (cfg.gameRegion.t * h).toInt()
        val y1 = (cfg.gameRegion.b * h).toInt().coerceAtMost(h)
        val m = finder.obstacleMask
        java.util.Arrays.fill(m, 0.toByte())
        for (y in y0 until y1) {
            if (!lanes.valid(y)) continue
            for (x in lanes.extL[y]..lanes.extR[y]) {
                val i = y * w + x
                if (!Palette.isRoad(p.hue[i], p.sat[i], p.value[i]) && !Palette.isWhite(p.sat[i], p.value[i])) m[i] = 1
            }
        }
        finder.close3x3(m, y0, y1)
        val minArea = (maxOf(4f, 30f - 36f * cfg.obstacleSensitivity) * sc * sc).toInt().coerceAtLeast(3)
        val out = ArrayList<Detection>()
        for (b in finder.components(m, p, y0, y1, minArea)) {
            if (player != null && b.bottom > player.top + 4 * sc &&
                b.x < player.cx + 16 * sc && b.right > player.cx - 16 * sc) continue
            // sparse hazards (yellow-black barriers) have low fill; water/rail artefacts have no yellow
            if (!(b.solidity >= 0.30f || (b.solidity >= 0.15f && b.yellowFrac >= 0.12f))) continue
            val row = (b.bottom - 1).coerceIn(0, h - 1)
            if (lanes.valid(row) && b.w <= 8 * sc &&
                (b.x <= lanes.extL[row] + 3 * sc || b.right >= lanes.extR[row] - 3 * sc)) continue
            if (ignored(b)) continue
            if (overlapsEnergy(b, energies)) continue
            val rl = lanes.toRoad(b.x.toFloat(), row)
            val rr = lanes.toRoad(b.right.toFloat(), row)
            val size = minOf(1f, b.area / (45f * sc * sc))
            val conf = (0.35f + 0.45f * size + 0.2f * minOf(1f, b.solidity * 1.5f)).coerceIn(0f, 1f)
            out.add(Detection(ObjectKind.OBSTACLE, b, rl, rr, b.y.toFloat() / h, b.bottom.toFloat() / h, conf))
        }
        return out
    }

    private fun overlapsEnergy(b: Blob, energies: List<Detection>): Boolean {
        val bx = b.x + b.w / 2f
        val by = b.y + b.h / 2f
        for (e in energies) {
            val eb = e.blob
            val ex = eb.x + eb.w / 2f
            val ey = eb.y + eb.h / 2f
            if (Math.abs(bx - ex) < maxOf(b.w, eb.w) / 2f + 2f && Math.abs(by - ey) < maxOf(b.h, eb.h) / 2f + 2f) return true
        }
        return false
    }
}
