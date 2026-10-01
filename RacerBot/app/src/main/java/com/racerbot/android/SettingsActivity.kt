package com.racerbot.android

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.racerbot.core.BotConfig
import com.racerbot.core.ControlMode
import com.racerbot.core.NRect
import kotlin.math.roundToInt

/** All tunables + calibration of regions. Every change is saved immediately (the calibration profile). */
class SettingsActivity : Activity() {
    private lateinit var repo: SettingsRepository
    private lateinit var col: LinearLayout
    private var dens = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        dens = resources.displayMetrics.density
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dens).toInt(), (20 * dens).toInt(), (16 * dens).toInt(), (24 * dens).toInt())
        }
        build()
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun build() {
        col.removeAllViews()
        val c = repo.current
        header("Chung")
        sw("Tự khởi động lại khi game over (cần đặt điểm chạm bên dưới)", c.autoRestart) { v -> repo.update { it.copy(autoRestart = v) } }
        sw("Lớp phủ debug", c.debugOverlay) { v -> repo.update { it.copy(debugOverlay = v) } }
        sw("Lớp phủ debug TOÀN MÀN HÌNH khi chạy (có thể gây nhiễu nhận diện)", c.debugFull) { v -> repo.update { it.copy(debugFull = v) } }
        sw("Quét chữ xác minh/CAPTCHA để tự dừng", c.securityScan) { v -> repo.update { it.copy(securityScan = v) } }

        header("Nhận diện & ưu tiên")
        fs("Độ nhạy chướng ngại", 0f, 1f, { it.obstacleSensitivity }, { x, v -> x.copy(obstacleSensitivity = v) })
        fs("Độ nhạy vật phẩm năng lượng", 0f, 1f, { it.energySensitivity }, { x, v -> x.copy(energySensitivity = v) })
        fs("Ưu tiên lấy năng lượng", 0f, 1f, { it.energyPriority }, { x, v -> x.copy(energyPriority = v) })
        fs("Ưu tiên né chướng ngại", 0f, 1f, { it.avoidPriority }, { x, v -> x.copy(avoidPriority = v) })
        fs("Ngưỡng độ tin cậy (dưới ngưỡng: CHỜ, không vuốt)", 0f, 1f, { it.confidenceThreshold }, { x, v -> x.copy(confidenceThreshold = v) })
        isl("Số vị trí/làn lập kế hoạch", 2, 9, { it.laneCount }, { x, v -> x.copy(laneCount = v) })
        fs("Nửa bề rộng xe (theo bề rộng đường)", 0.05f, 0.30f, { it.carHalfWidth }, { x, v -> x.copy(carHalfWidth = v) })
        isl("Độ rộng ảnh xử lý (px)", 120, 270, { it.procWidth }, { x, v -> x.copy(procWidth = v) })

        header("Vuốt")
        sw("Chế độ vuốt rời rạc (1 vuốt = 1 bước làn); tắt = vuốt tỉ lệ, tự học độ nhạy", c.controlMode == ControlMode.DISCRETE_SWIPE) { v ->
            repo.update { it.copy(controlMode = if (v) ControlMode.DISCRETE_SWIPE else ControlMode.PROPORTIONAL_DRAG) }
        }
        sw("Đảo hướng vuốt", c.invertSwipe) { v -> repo.update { it.copy(invertSwipe = v) } }
        fs("Quãng vuốt rời rạc (tỉ lệ bề rộng màn hình)", 0.05f, 0.5f, { it.swipeDistance }, { x, v -> x.copy(swipeDistance = v) })
        isl("Thời lượng vuốt (ms)", 40, 400, { it.swipeDurationMs }, { x, v -> x.copy(swipeDurationMs = v) })
        isl("Cooldown giữa hai thao tác (ms)", 100, 1000, { it.actionCooldownMs }, { x, v -> x.copy(actionCooldownMs = v) })
        isl("Chu kỳ quyết định tối thiểu (ms)", 20, 200, { it.decisionIntervalMs }, { x, v -> x.copy(decisionIntervalMs = v) })
        isl("Thời gian ở lại làn tối thiểu (ms)", 0, 1500, { it.minLaneStayMs }, { x, v -> x.copy(minLaneStayMs = v) })
        fs("Độ nhạy vuốt (px ngón / px xe) — tự học", 0.3f, 8f, { it.dragGain }, { x, v -> x.copy(dragGain = v) })
        fs("Độ cao điểm vuốt (tỉ lệ chiều cao)", 0.5f, 0.98f, { it.gestureAnchorY }, { x, v -> x.copy(gestureAnchorY = v) })

        header("Vùng hiệu chuẩn (tỉ lệ 0..1 của màn hình)")
        fs("Vùng game: trên", 0f, 0.5f, { it.gameRegion.t }, { x, v -> x.copy(gameRegion = x.gameRegion.copy(t = v)) })
        fs("Vùng game: dưới", 0.5f, 1f, { it.gameRegion.b }, { x, v -> x.copy(gameRegion = x.gameRegion.copy(b = v)) })
        fs("Vùng xe: trên", 0.4f, 0.8f, { it.playerRegion.t }, { x, v -> x.copy(playerRegion = x.playerRegion.copy(t = v)) })
        fs("Vùng xe: dưới", 0.5f, 0.9f, { it.playerRegion.b }, { x, v -> x.copy(playerRegion = x.playerRegion.copy(b = v)) })
        fs("Thanh năng lượng: trái", 0f, 0.6f, { it.energyBarRegion.l }, { x, v -> x.copy(energyBarRegion = x.energyBarRegion.copy(l = v)) })
        fs("Thanh năng lượng: phải", 0.4f, 1f, { it.energyBarRegion.r }, { x, v -> x.copy(energyBarRegion = x.energyBarRegion.copy(r = v)) })
        fs("Thanh năng lượng: trên", 0f, 0.4f, { it.energyBarRegion.t }, { x, v -> x.copy(energyBarRegion = x.energyBarRegion.copy(t = v)) })
        fs("Thanh năng lượng: dưới", 0f, 0.4f, { it.energyBarRegion.b }, { x, v -> x.copy(energyBarRegion = x.energyBarRegion.copy(b = v)) })
        isl("Số ô của thanh năng lượng", 3, 12, { it.energySegments }, { x, v -> x.copy(energySegments = v) })

        header("Tự khởi động lại — điểm chạm nút chơi lại (cả hai > 0 mới có hiệu lực)")
        fs("Điểm chạm X", 0f, 1f, { it.restartTapX.coerceAtLeast(0f) }, { x, v -> x.copy(restartTapX = v) })
        fs("Điểm chạm Y", 0f, 1f, { it.restartTapY.coerceAtLeast(0f) }, { x, v -> x.copy(restartTapY = v) })

        header("Giới hạn ứng dụng (tuỳ chọn)")
        col.addView(TextView(this).apply { text = "Package của ứng dụng chứa game; để trống = không giới hạn. Chỉ gửi thao tác khi ứng dụng này ở nền trước." })
        col.addView(EditText(this).apply {
            setText(c.targetPackage)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { repo.update { it.copy(targetPackage = s?.toString()?.trim() ?: "") } }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        })

        col.addView(Button(this).apply {
            text = "ĐẶT LẠI MẶC ĐỊNH"
            setOnClickListener { repo.reset(); build() }
        })
        col.addView(TextView(this).apply { text = "Mọi thay đổi được lưu ngay (đây là hồ sơ hiệu chuẩn) và áp dụng trực tiếp cho phiên đang chạy." })
    }

    private fun header(t: String) {
        col.addView(TextView(this).apply {
            text = t; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (18 * dens).toInt(), 0, (4 * dens).toInt())
        })
    }

    private fun sw(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        col.addView(Switch(this).apply {
            text = label; isChecked = value
            setOnCheckedChangeListener { _, b -> onChange(b) }
        })
    }

    private fun fs(label: String, min: Float, max: Float, get: (BotConfig) -> Float, set: (BotConfig, Float) -> BotConfig, fmt: String = "%.2f") {
        val tv = TextView(this)
        val v0 = get(repo.current).coerceIn(min, max)
        tv.text = label + ": " + fmt.format(v0)
        val sb = SeekBar(this).apply {
            this.max = 1000
            progress = ((v0 - min) / (max - min) * 1000f).toInt().coerceIn(0, 1000)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + (max - min) * p / 1000f
                    tv.text = label + ": " + fmt.format(v)
                    if (fromUser) repo.update { set(it, v) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        col.addView(tv)
        col.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun isl(label: String, min: Int, max: Int, get: (BotConfig) -> Int, set: (BotConfig, Int) -> BotConfig) {
        fs(label, min.toFloat(), max.toFloat(), { get(it).toFloat() }, { c, v -> set(c, v.roundToInt()) }, "%.0f")
    }
}
