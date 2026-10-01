package com.racerbot.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Permission walk-through + entry point. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
        }
        fun text(t: String, size: Float = 14f, bold: Boolean = false): TextView = TextView(this).apply {
            text = t; textSize = size
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
        }
        fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
            text = t; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { onClick() }
        }

        col.addView(text("Racer AutoPilot", 22f, true))
        col.addView(text("Bot chơi game đua xe trong video: nhìn màn hình → nhận diện → dự đoán va chạm → vuốt. Mọi xử lý hình ảnh chạy ngay trên máy, không gửi dữ liệu đi đâu (ứng dụng không có quyền Internet)."))
        status = text("", 13f)
        status.setTextColor(Color.DKGRAY)
        col.addView(status)

        col.addView(text("Bước 1 — Dịch vụ Trợ năng", 16f, true))
        col.addView(text("Cần để bot THỰC HIỆN thao tác vuốt, và để dừng bot khi màn hình có chữ xác minh/CAPTCHA. Cài đặt → Trợ năng → Ứng dụng đã tải xuống → Racer AutoPilot → Bật."))
        col.addView(button("Mở cài đặt Trợ năng") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        col.addView(text("Bước 2 — Hiển thị trên ứng dụng khác", 16f, true))
        col.addView(text("Cần cho bảng điều khiển nổi và lớp phủ debug."))
        col.addView(button("Cấp quyền hiển thị") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        col.addView(text("Bước 3 — Quyền ghi màn hình", 16f, true))
        col.addView(text("Hộp thoại chuẩn của Android. Bot dùng nó để NHÌN game (không lưu, không gửi)."))
        col.addView(button("Bắt đầu phiên (hiện bảng nổi)") {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Hãy làm Bước 2 trước", Toast.LENGTH_LONG).show() }
            else {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION")
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
            }
        })

        col.addView(text("Sau đó", 16f, true))
        col.addView(text("1) Mở game. 2) Chạm CALIBRATE trên bảng nổi để xem lớp phủ (vạch trắng = mép đường/làn, vàng = vật phẩm, đỏ = nguy hiểm, xanh dương = xe). 3) Chạm CALIBRATE lần nữa để vuốt thử và để bot học độ nhạy vuốt. 4) Chạm STOP để thoát hiệu chuẩn, rồi START. Dừng ngay: nút STOP trên bảng, nút DỪNG ở thông báo, hoặc tắt dịch vụ Trợ năng."))
        col.addView(button("Cài đặt / Hiệu chuẩn") { startActivity(Intent(this, SettingsActivity::class.java)) })
        col.addView(button("Dừng dịch vụ ngay") {
            startService(Intent(this, BotService::class.java).setAction(BotService.ACTION_STOP))
        })
        col.addView(text("Lưu ý: dùng bot trong game có thể vi phạm điều khoản của game/nhà phát hành. Bạn tự chịu trách nhiệm. Bot sẽ dừng khi thấy màn hình xác minh/CAPTCHA và không cố vượt qua.", 12f))
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val a11y = BotAccessibilityService.instance != null
        val ov = Settings.canDrawOverlays(this)
        status.text = "Trợ năng: " + (if (a11y) "ĐÃ BẬT" else "chưa bật") +
            "\nHiển thị trên ứng dụng khác: " + (if (ov) "ĐÃ CẤP" else "chưa cấp") +
            "\nPhiên bot: " + (if (BotService.running) "ĐANG CHẠY" else "chưa chạy")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, BotService::class.java)
                    .putExtra(BotService.EXTRA_CODE, resultCode)
                    .putExtra(BotService.EXTRA_DATA, data)
                startForegroundService(i)
                Toast.makeText(this, "Mở game rồi dùng bảng nổi Racer Bot", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Bạn đã từ chối quyền ghi màn hình", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object { private const val REQ_CAPTURE = 1001 }
}
