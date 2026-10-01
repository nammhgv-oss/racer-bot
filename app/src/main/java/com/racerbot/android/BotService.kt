package com.racerbot.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.racerbot.core.BotMode

/** Foreground service that owns capture, the bot loop and the overlays. */
class BotService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var capture: ScreenCaptureManager? = null
    private var controller: BotController? = null
    private var panel: FloatingControlPanel? = null
    private var overlay: DebugOverlay? = null
    private lateinit var repo: SettingsRepository
    private var alive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        if (alive) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        startForegroundCompat()
        repo = SettingsRepository.get(this)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (projection == null) { toast("Không lấy được quyền ghi màn hình"); shutdown(); return START_NOT_STICKY }

        val p = FloatingControlPanel(this, object : FloatingControlPanel.Actions {
            override fun onStart() {
                if (BotAccessibilityService.instance == null) { toast("Hãy bật dịch vụ Trợ năng trước"); return }
                controller?.start()
            }
            override fun onStop() { controller?.stop(); overlay?.hide() }
            override fun onPause() { controller?.pause() }
            override fun onCalibrate() {
                val c = controller ?: return
                if (c.mode == BotMode.CALIBRATING) {
                    c.testSwipe()
                    if (c.calibration.applyAuto()) toast("Đã lưu vị trí đầu xe. Vuốt thử để học độ nhạy.")
                } else {
                    c.calibrate(true)
                    toast("Hiệu chuẩn: xem lớp phủ, chạm CALIBRATE lần nữa để vuốt thử. STOP để thoát.")
                }
            }
            override fun onSettings() {
                startActivity(Intent(this@BotService, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        val o = DebugOverlay(this)
        panel = p
        overlay = o
        val c = BotController(
            this, repo,
            onStatus = { s -> main.post { panel?.update(s) } },
            onOverlay = { snap -> main.post { applyOverlay(snap.status.mode, snap.cfg.debugOverlay, snap.cfg.debugFull); overlay?.update(snap) } },
            ignoreRects = { listOfNotNull(panel?.bounds(), overlay?.bounds()) }
        )
        controller = c
        val cap = ScreenCaptureManager(this, projection, { f -> c.submit(f) }, { main.post { shutdown() } })
        capture = cap
        try { cap.start(repo.current.procWidth) } catch (e: Exception) { toast("Không bắt đầu được chụp màn hình"); shutdown(); return START_NOT_STICKY }
        alive = true
        running = true
        main.post { p.show() }
        main.postDelayed(watchdog, 1500L)
        toast("Racer AutoPilot sẵn sàng. Mở game rồi chạm CALIBRATE / START.")
        return START_NOT_STICKY
    }

    private fun applyOverlay(mode: BotMode, enabled: Boolean, fullCfg: Boolean) {
        val o = overlay ?: return
        when {
            mode == BotMode.IDLE || !enabled -> o.hide()
            mode == BotMode.CALIBRATING -> o.show(true)
            else -> o.show(fullCfg)
        }
    }

    /** Handles rotation / resolution change and Accessibility disconnects. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!alive) return
            try { capture?.restartIfGeometryChanged(repo.current.procWidth) } catch (e: Exception) { }
            val c = controller
            if (c != null && c.mode == BotMode.RUNNING && BotAccessibilityService.instance == null) {
                c.pause()
                c.setWarning("Dịch vụ Trợ năng bị ngắt — bot đã tạm dừng")
            }
            main.postDelayed(this, 1500L)
        }
    }

    private fun shutdown() {
        alive = false
        running = false
        main.removeCallbacks(watchdog)
        controller?.release(); controller = null
        capture?.stop(); capture = null
        panel?.hide(); panel = null
        overlay?.hide(); overlay = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        if (alive) shutdown()
        super.onDestroy()
    }

    private fun toast(t: String) { main.post { Toast.makeText(applicationContext, t, Toast.LENGTH_LONG).show() } }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Racer AutoPilot", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, BotService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Racer AutoPilot")
            .setContentText("Xử lý màn hình cục bộ. Chạm DỪNG để tắt ngay.")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_delete, "DỪNG", stopPi).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIF_ID, n)
    }

    companion object {
        const val ACTION_STOP = "com.racerbot.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "racerbot"
        private const val NOTIF_ID = 42
        @Volatile var running = false
    }
}
