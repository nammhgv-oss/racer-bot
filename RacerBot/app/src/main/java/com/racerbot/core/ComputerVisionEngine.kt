package com.racerbot.core

/** Orchestrates preprocessing + all detectors for one frame. Not thread-safe (single worker). */
class ComputerVisionEngine {
    private var w = 0
    private var h = 0
    private var planes: HsvPlanes? = null
    private var finder: BlobFinder? = null
    private val playerDetector = PlayerDetector()
    private val laneDetector = LaneDetector()
    private val energyDetector = EnergyDetector()
    private val obstacleDetector = ObstacleDetector()
    private var lastSeed = -1
    private var prevSamples: IntArray? = null

    /** Normalised screen rectangles occupied by our own overlays; detections inside are dropped. */
    @Volatile var ignoreRects: List<NRect> = emptyList()

    fun process(frame: Frame, cfg: BotConfig): VisionResult {
        val t0 = System.nanoTime()
        if (frame.width != w || frame.height != h || planes == null) {
            w = frame.width; h = frame.height
            planes = HsvPlanes(w, h)
            finder = BlobFinder(w, h)
            lastSeed = -1
            prevSamples = null
        }
        val p = planes!!
        val f = finder!!
        p.fill(frame.pixels)

        val player = playerDetector.detect(p, cfg)
        if (player != null) lastSeed = player.cx.toInt()
        val lanes = laneDetector.detect(p, cfg, if (lastSeed >= 0) lastSeed else w / 2)
        val bar = energyDetector.readBar(p, cfg)

        val rects = ignoreRects
        val ignored: (Blob) -> Boolean = { b ->
            val cx = (b.x + b.w / 2f) / w
            val cy = (b.y + b.h / 2f) / h
            var hit = false
            for (r in rects) if (r.contains(cx, cy)) { hit = true; break }
            hit
        }
        val energies = energyDetector.detectCollectibles(p, f, cfg, player, lanes, ignored)
        val obstacles = obstacleDetector.detect(p, f, cfg, player, lanes, energies, ignored)

        // brightness + motion from a sparse grid (motion ~0 => frozen/paused game)
        val y0 = (cfg.gameRegion.t * h).toInt()
        val y1 = (cfg.gameRegion.b * h).toInt().coerceAtMost(h)
        val step = 3
        val cols = (w + step - 1) / step
        val rows = ((y1 - y0) + step - 1) / step
        val cur = IntArray(cols * rows)
        var sum = 0L
        var k = 0
        var yy = y0
        while (yy < y1) {
            var xx = 0
            while (xx < w) { val v = p.value[yy * w + xx]; cur[k++] = v; sum += v; xx += step }
            yy += step
        }
        val prev = prevSamples
        var motion = 0f
        if (prev != null && prev.size == cur.size) {
            var d = 0L
            for (i in cur.indices) d += Math.abs(cur[i] - prev[i])
            motion = d.toFloat() / cur.size
        }
        prevSamples = cur
        val mean = if (cur.isEmpty()) 0f else sum.toFloat() / cur.size

        return VisionResult(w, h, player, lanes, obstacles, energies, bar, mean, motion,
            (System.nanoTime() - t0) / 1_000_000f)
    }
}
