package com.racerbot.android

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.racerbot.core.BotMode
import com.racerbot.core.NRect

/** Compact, draggable, collapsible control panel. Collapse it while playing. */
class FloatingControlPanel(private val ctx: Context, private val actions: Actions) {
    interface Actions {
        fun onStart(); fun onStop(); fun onPause(); fun onCalibrate(); fun onSettings()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val d = ctx.resources.displayMetrics.density
    private var root: LinearLayout? = null
    private var body: LinearLayout? = null
    private var statusText: TextView? = null
    private var lp: WindowManager.LayoutParams? = null

    fun bounds(): NRect? {
        val r = root ?: return null
        val p = lp ?: return null
        val (sw, sh) = ScreenInfo.realSize(ctx)
        if (r.width == 0) return null
        return NRect(p.x.toFloat() / sw, p.y.toFloat() / sh, (p.x + r.width).toFloat() / sw, (p.y + r.height).toFloat() / sh)
    }

    fun show() {
        if (root != null) return
        val (sw, sh) = ScreenInfo.realSize(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(215, 16, 16, 28)); cornerRadius = 10f * d }
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
        }
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(ctx).apply {
            text = "≡ RACER BOT"; setTextColor(Color.WHITE); textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val collapse = TextView(ctx).apply {
            text = "—"; setTextColor(Color.WHITE); textSize = 14f
            setPadding((10 * d).toInt(), 0, (4 * d).toInt(), 0)
        }
        header.addView(title); header.addView(collapse)

        val st = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "BOT: IDLE"
        }
        statusText = st
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        b.addView(st)
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(btn("START") { actions.onStart() })
        row1.addView(btn("PAUSE") { actions.onPause() })
        row1.addView(btn("STOP") { actions.onStop() })
        row2.addView(btn("CALIBRATE") { actions.onCalibrate() })
        row2.addView(btn("SETTINGS") { actions.onSettings() })
        b.addView(row1); b.addView(row2)
        body = b
        box.addView(header); box.addView(b)

        val params = WindowManager.LayoutParams(
            (sw * 0.5f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = sw / 2
        params.y = (sh * 0.62f).toInt()
        lp = params

        var ix = 0; var iy = 0; var dx = 0f; var dy = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; dx = e.rawX; dy = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (ix + (e.rawX - dx)).toInt()
                    params.y = (iy + (e.rawY - dy)).toInt()
                    try { wm.updateViewLayout(box, params) } catch (ex: Exception) { }
                    true
                }
                else -> false
            }
        }
        collapse.setOnClickListener {
            val vis = b.visibility == View.VISIBLE
            b.visibility = if (vis) View.GONE else View.VISIBLE
            collapse.text = if (vis) "+" else "—"
            params.width = if (vis) WindowManager.LayoutParams.WRAP_CONTENT else (sw * 0.5f).toInt()
            try { wm.updateViewLayout(box, params) } catch (ex: Exception) { }
        }
        try { wm.addView(box, params); root = box } catch (e: Exception) { root = null }
    }

    private fun btn(label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label; textSize = 9f; isAllCaps = false
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        layoutParams = LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f)
        setOnClickListener { onClick() }
    }

    fun update(s: BotStatus) {
        val modeTxt = when (s.mode) {
            BotMode.RUNNING -> "RUNNING"; BotMode.PAUSED -> "PAUSED"
            BotMode.CALIBRATING -> "CALIBRATING"; BotMode.IDLE -> "IDLE"
        }
        val sb = StringBuilder()
        sb.append("BOT: ").append(modeTxt).append(" / ").append(s.gameState).append('\n')
        sb.append("LANE: ").append(s.laneName).append("  FPS ").append("%.0f".format(s.fps)).append('\n')
        sb.append("OBST ").append(s.obstacleCount).append("  EN ").append(s.energyCount)
            .append("  NRG ").append(s.energyText).append('\n')
        sb.append("ACT: ").append(s.lastAction)
        if (s.warning.isNotEmpty()) sb.append('\n').append("! ").append(s.warning)
        statusText?.text = sb.toString()
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        root = null; body = null; statusText = null; lp = null
    }
}
