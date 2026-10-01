package com.racerbot.core

/** HSV planes with OpenCV scaling: H 0..179, S 0..255, V 0..255. */
class HsvPlanes(val w: Int, val h: Int) {
    val hue = IntArray(w * h)
    val sat = IntArray(w * h)
    val value = IntArray(w * h)

    fun fill(px: IntArray) {
        for (i in 0 until w * h) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val d = mx - mn
            value[i] = mx
            sat[i] = if (mx == 0) 0 else 255 * d / mx
            var hh = 0
            if (d != 0) {
                hh = when (mx) {
                    r -> 30 * (g - b) / d
                    g -> 60 + 30 * (b - r) / d
                    else -> 120 + 30 * (r - g) / d
                }
                if (hh < 0) hh += 180
            }
            hue[i] = hh
        }
    }
}

object Palette {
    fun isRoad(h: Int, s: Int, v: Int) = h in 100..135 && s >= 110 && v in 18..115
    fun isWhite(s: Int, v: Int) = s < 60 && v > 170
    fun isPlume(s: Int, v: Int) = v >= 225 && s <= 120
    fun isCarRed(h: Int, s: Int, v: Int) = (h <= 8 || h >= 172) && s >= 120 && v >= 90
    fun isCollectYellow(h: Int, s: Int, v: Int) = h in 20..38 && s >= 120 && v >= 200
    fun isCollectRed(h: Int, s: Int, v: Int) = (h <= 6 || h >= 174) && s >= 150 && v >= 140
    fun isBlobYellow(h: Int, s: Int, v: Int) = h in 18..40 && s >= 90 && v >= 150
    fun isBlobRed(h: Int, s: Int, v: Int) = (h <= 8 || h >= 172) && s >= 130 && v >= 110
}
