package com.racerbot.core

class Track(
    val id: Int, val kind: ObjectKind,
    var rL: Float, var rR: Float, var yTop: Float, var yBottom: Float,
    var conf: Float, var lastMs: Long
) {
    var vy = 0f          // normalised screen heights per second (positive = approaching)
    var hits = 1
    var misses = 0
    val rC: Float get() = (rL + rR) / 2f
    val confirmed: Boolean get() = hits >= 2

    /**
     * Real objects approach with vy ~ a*(y-horizon)^2 (measured on the video). A blob that has been
     * tracked for a while but does not move that way is screen-static clutter (rails, water).
     */
    fun moving(horizon: Float, a: Float): Boolean {
        if (hits < 6) return true
        val u = (yBottom - horizon).coerceAtLeast(0.02f)
        return vy >= 0.35f * a * u * u
    }
    /** Confidence discounted until the object has been seen on several frames. */
    val effConf: Float get() = conf * minOf(1f, hits / 3f)
}

/** Greedy nearest-neighbour tracker in road-relative coordinates. */
class ObjectTracker {
    private val tracks = ArrayList<Track>()
    private var nextId = 1

    fun reset() { tracks.clear() }

    fun update(dets: List<Detection>, now: Long): List<Track> {
        val used = HashSet<Int>()
        for (d in dets) {
            val dc = (d.rL + d.rR) / 2f
            var best: Track? = null
            var bestCost = Float.MAX_VALUE
            for (t in tracks) {
                if (t.kind != d.kind || used.contains(t.id)) continue
                val dt = (now - t.lastMs).coerceAtLeast(1L) / 1000f
                val dy = d.yBottom - t.yBottom
                if (Math.abs(dc - t.rC) > 0.25f) continue
                if (dy < -0.05f || dy > 0.05f + 0.8f * dt) continue
                val cost = Math.abs(dc - t.rC) * 3f + Math.abs(dy)
                if (cost < bestCost) { bestCost = cost; best = t }
            }
            if (best != null) {
                val t = best
                val dt = (now - t.lastMs) / 1000f
                if (dt >= 0.015f) {
                    val inst = (d.yBottom - t.yBottom) / dt
                    t.vy = 0.6f * t.vy + 0.4f * inst
                }
                t.rL = 0.5f * t.rL + 0.5f * d.rL
                t.rR = 0.5f * t.rR + 0.5f * d.rR
                t.yTop = d.yTop
                t.yBottom = d.yBottom
                t.conf = 0.5f * t.conf + 0.5f * d.confidence
                t.lastMs = now
                t.hits++
                t.misses = 0
                used.add(t.id)
            } else {
                val t = Track(nextId++, d.kind, d.rL, d.rR, d.yTop, d.yBottom, d.confidence, now)
                tracks.add(t)
                used.add(t.id)
            }
        }
        val it = tracks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (!used.contains(t.id)) t.misses++
            if (t.misses > 4 || now - t.lastMs > 350) it.remove()
        }
        return ArrayList(tracks)
    }
}
