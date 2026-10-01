package com.racerbot.android

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.racerbot.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class BotStatus(
    val mode: BotMode = BotMode.IDLE,
    val gameState: GameState = GameState.UNKNOWN,
    val laneName: String = "—",
    val obstacleCount: Int = 0,
    val energyCount: Int = 0,
    val energyText: String = "—",
    val lastAction: String = "—",
    val fps: Float = 0f,
    val warning: String = "",
    val decisionConf: Float = 0f,
    val playerConf: Float = 0f
)

class OverlaySnapshot(
    val vr: VisionResult, val tracks: List<Track>, val threats: List<Threat>,
    val decision: Decision?, val status: BotStatus, val cfg: BotConfig
)

/**
 * OBSERVE -> UNDERSTAND -> PREDICT -> DECIDE -> ACT loop. Runs on one background coroutine and only
 * ever sends input while the state machine says PLAYING and the mode is RUNNING.
 */
class BotController(
    private val ctx: Context,
    private val repo: SettingsRepository,
    private val onStatus: (BotStatus) -> Unit,
    private val onOverlay: (OverlaySnapshot) -> Unit,
    private val ignoreRects: () -> List<NRect>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<Frame>(Channel.CONFLATED)
    private val engine = ComputerVisionEngine()
    private val tracker = ObjectTracker()
    private val predictor = CollisionPredictor()
    private val decisionEngine = DecisionEngine()
    private val stateDetector = GameStateDetector()
    val swipe = SwipeController(ctx, repo)
    val calibration = CalibrationManager(repo)

    @Volatile var mode = BotMode.IDLE
        private set
    @Volatile private var warning = ""
    @Volatile private var securityTripped = false
    private var lastProcessMs = 0L
    private var fps = 0f
    private var lastSecurityScan = 0L
    private var lastRestartMs = 0L
    private var restartTries = 0
    private var lastVision: VisionResult? = null
    private var testDir = 1

    init {
        scope.launch {
            for (f in channel) {
                try { process(f) } catch (e: Exception) { Log.e("RacerBot", "process failed", e) }
            }
        }
    }

    fun submit(frame: Frame) { channel.trySend(frame) }

    fun start() {
        securityTripped = false
        warning = ""
        tracker.reset(); decisionEngine.reset()
        mode = BotMode.RUNNING
    }

    fun stop() { mode = BotMode.IDLE; tracker.reset(); decisionEngine.reset(); publishIdle() }
    fun pause() { if (mode == BotMode.RUNNING) mode = BotMode.PAUSED else if (mode == BotMode.PAUSED) mode = BotMode.RUNNING }
    fun calibrate(on: Boolean) { mode = if (on) BotMode.CALIBRATING else BotMode.IDLE; if (!on) publishIdle() }
    fun setWarning(w: String) { warning = w }

    fun release() { mode = BotMode.IDLE; channel.close(); scope.cancel() }

    private fun publishIdle() { onStatus(BotStatus(mode = mode, lastAction = swipe.lastActionText, warning = warning)) }

    /** Calibration helper: sends one swipe and lets the closed loop measure the car's response. */
    fun testSwipe() {
        val vr = lastVision ?: return
        val p = vr.player ?: return
        val cfg = repo.current
        val now = SystemClock.elapsedRealtime()
        if (!swipe.canAct(now, cfg)) return
        val (sw, _) = ScreenInfo.realSize(ctx)
        val row = (p.bottom - 1f).toInt().coerceIn(0, vr.height - 1)
        val cx = vr.lanes.toScreen(vr.lanes.toRoad(p.cx, row), row) * sw / vr.width
        swipe.move(testDir * 0.15f * sw, cx, cfg, now)
        testDir = -testDir
    }

    private fun laneName(i: Int, n: Int): String = when {
        n == 3 -> arrayOf("LEFT", "CENTER", "RIGHT")[i.coerceIn(0, 2)]
        i == 0 -> "LEFT"
        i == n - 1 -> "RIGHT"
        n % 2 == 1 && i == n / 2 -> "CENTER"
        else -> "LANE ${i + 1}/$n"
    }

    private fun process(frame: Frame) {
        val m = mode
        if (m == BotMode.IDLE) return
        val now = SystemClock.elapsedRealtime()
        val cfg = repo.current
        if (now - lastProcessMs < cfg.decisionIntervalMs) return
        if (lastProcessMs > 0) {
            val inst = 1000f / (now - lastProcessMs).coerceAtLeast(1L)
            fps = 0.9f * fps + 0.1f * inst
        }
        lastProcessMs = now

        engine.ignoreRects = ignoreRects()
        val vr = engine.process(frame, cfg)
        lastVision = vr
        calibration.observe(vr)

        // ---- safety: verification / CAPTCHA prompts and wrong foreground app ----
        if (cfg.securityScan && now - lastSecurityScan > 1500L) {
            lastSecurityScan = now
            if (BotAccessibilityService.instance?.securityPromptVisible() == true) securityTripped = true
        }
        val wrongApp = cfg.targetPackage.isNotEmpty() &&
            BotAccessibilityService.foregroundPackage.isNotEmpty() &&
            BotAccessibilityService.foregroundPackage != cfg.targetPackage

        val state = stateDetector.update(vr, securityTripped, now, cfg)
        if (state == GameState.SECURITY_CHALLENGE && mode == BotMode.RUNNING) {
            mode = BotMode.PAUSED
            warning = "Phát hiện màn hình xác minh/CAPTCHA — bot đã DỪNG. Hãy tự xử lý, bot không né/giải."
        }
        if (wrongApp && mode == BotMode.RUNNING) warning = "Đang ở ứng dụng khác — không gửi thao tác."
        else if (!securityTripped && warning.startsWith("Đang ở")) warning = ""

        val playing = state == GameState.PLAYING && vr.player != null && !wrongApp
        var decision: Decision? = null
        var tracks: List<Track> = emptyList()
        var threats: List<Threat> = emptyList()

        if (playing) {
            val p = vr.player!!
            val row = (p.bottom - 1f).toInt().coerceIn(0, vr.height - 1)
            val carR = vr.lanes.toRoad(p.cx, row)
            tracks = tracker.update(vr.obstacles + vr.energies, now)
            val carFront = p.top / vr.height
            predictor.configure(cfg)
            threats = predictor.assess(tracks, carR, cfg, carFront, cfg.perspectiveHorizonY)
            predictor.learn(tracks, cfg.perspectiveHorizonY)
            val baseConf = minOf(p.confidence, vr.lanes.confidence.coerceAtLeast(0.2f))
            decision = decisionEngine.decide(now, carR, threats, vr.energyBar, baseConf, cfg)

            val (sw, _) = ScreenInfo.realSize(ctx)
            val scale = sw.toFloat() / vr.width
            val carX = vr.lanes.toScreen(carR, row) * scale
            swipe.observe(carX, cfg, now)

            if (mode == BotMode.RUNNING && decision.move && swipe.canAct(now, cfg)) {
                val tx = vr.lanes.toScreen(decision.targetR, row) * scale
                if (swipe.move(tx - carX, carX, cfg, now)) decisionEngine.notifyMoved(now, decision.direction)
            }
            restartTries = 0
        } else {
            tracker.reset(); decisionEngine.reset()
            if (mode == BotMode.RUNNING && state == GameState.GAME_OVER && cfg.autoRestart &&
                cfg.restartTapX >= 0f && cfg.restartTapY >= 0f &&
                restartTries < 3 && now - lastRestartMs > 3000L
            ) {
                if (swipe.tap(cfg.restartTapX, cfg.restartTapY)) { restartTries++; lastRestartMs = now }
            }
        }

        val n = decision?.positions?.size ?: cfg.laneCount
        val laneTxt = decision?.let { laneName(it.currentIndex, n) } ?: "—"
        val status = BotStatus(
            mode = mode, gameState = state, laneName = laneTxt,
            obstacleCount = vr.obstacles.size, energyCount = vr.energies.size,
            energyText = if (vr.energyBar.present) "${vr.energyBar.lit}/${vr.energyBar.segments}" else "—",
            lastAction = swipe.lastActionText, fps = fps,
            warning = if (warning.isNotEmpty()) warning else swipe.hint,
            decisionConf = decision?.confidence ?: 0f, playerConf = vr.player?.confidence ?: 0f
        )
        onStatus(status)
        onOverlay(OverlaySnapshot(vr, tracks, threats, decision, status, cfg))
    }
}
