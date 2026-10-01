package com.racerbot.core

/** Shared binary-mask helpers: 3x3 close and 8-connected component labelling. */
class BlobFinder(private val w: Int, private val h: Int) {
    val obstacleMask = ByteArray(w * h)
    val collectMask = ByteArray(w * h)
    private val tmp = ByteArray(w * h)
    private val label = IntArray(w * h)
    private val stack = IntArray(w * h)

    /** In-place 3x3 close (dilate then erode) on rows y0 until y1. */
    fun close3x3(m: ByteArray, y0: Int, y1: Int) {
        java.util.Arrays.fill(tmp, 0.toByte())
        for (y in y0 until y1) for (x in 0 until w) {
            var any = false
            loop@ for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    if (m[yy * w + xx].toInt() != 0) { any = true; break@loop }
                }
            }
            if (any) tmp[y * w + x] = 1
        }
        for (y in y0 until y1) for (x in 0 until w) {
            var all = true
            loop@ for (dy in -1..1) {
                val yy = y + dy
                for (dx in -1..1) {
                    val xx = x + dx
                    if (yy < 0 || yy >= h || xx < 0 || xx >= w || tmp[yy * w + xx].toInt() == 0) { all = false; break@loop }
                }
            }
            m[y * w + x] = if (all) 1 else 0
        }
    }

    fun components(m: ByteArray, p: HsvPlanes, y0: Int, y1: Int, minArea: Int): List<Blob> {
        java.util.Arrays.fill(label, 0)
        val out = ArrayList<Blob>()
        var next = 1
        for (y in y0 until y1) for (x in 0 until w) {
            val i0 = y * w + x
            if (m[i0].toInt() == 0 || label[i0] != 0) continue
            var sp = 0
            stack[sp++] = i0
            label[i0] = next
            var minX = x; var maxX = x; var minY = y; var maxY = y
            var area = 0; var dark = 0; var yel = 0; var red = 0
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % w
                val cy = c / w
                area++
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                val hh = p.hue[c]; val ss = p.sat[c]; val vv = p.value[c]
                if (vv < 70) dark++
                if (Palette.isBlobYellow(hh, ss, vv)) yel++
                if (Palette.isBlobRed(hh, ss, vv)) red++
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || nx >= w || ny < y0 || ny >= y1) continue
                    val ni = ny * w + nx
                    if (m[ni].toInt() != 0 && label[ni] == 0) { label[ni] = next; stack[sp++] = ni }
                }
            }
            next++
            if (area >= minArea) {
                out.add(Blob(minX, minY, maxX - minX + 1, maxY - minY + 1, area,
                    dark.toFloat() / area, yel.toFloat() / area, red.toFloat() / area))
            }
        }
        return out
    }
}
