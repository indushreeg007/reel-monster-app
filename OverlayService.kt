package com.reelmonster.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.reelmonster.app.MainActivity
import com.reelmonster.app.data.K
import com.reelmonster.app.data.Prefs
import com.reelmonster.app.data.Repo
import com.reelmonster.app.data.SettingsStore
import com.reelmonster.app.monster.Dialogues
import com.reelmonster.app.monster.MonsterView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var monster: MonsterView
    private lateinit var minBtn: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private var minimized = false
    private var prefs = Prefs()
    private var added = false
    private var reactionCount = 0

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        startFg()
        if (!added) build()
        return START_STICKY
    }

    private fun startFg() {
        val ch = "overlay"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(ch, "Monster overlay", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Reel Monster is keeping you company 👾").setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun pill(txt: String) = TextView(this).apply {
        text = txt; textSize = 12f; gravity = Gravity.CENTER; setTextColor(0xFF4A3F5C.toInt())
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCCFFFFFF.toInt()); setStroke(dp(1), 0xFFCDB4DB.toInt()) }
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(4); marginEnd = dp(4) }
    }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        bubble = TextView(this).apply {
            visibility = View.GONE; textSize = 13f; maxWidth = dp(220)
            setTextColor(0xFF4A3F5C.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xFFFFFDF5.toInt()); setStroke(dp(2), 0xFFCDB4DB.toInt()) }
        }
        monster = MonsterView(this)
        minBtn = pill("–")
        val close = pill("✕")
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(minBtn); addView(close) }
        root.addView(bubble); root.addView(monster); root.addView(controls)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(320) }
        wm.addView(root, lp); added = true

        // drag + tap
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        val slop = dp(8)
        monster.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; ox = lp.x; oy = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - sx; val dy = ev.rawY - sy
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true
                    if (moved) { lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt(); wm.updateViewLayout(root, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("route", "today"))
                    true
                }
                else -> false
            }
        }
        minBtn.setOnClickListener {
            minimized = !minimized
            monster.scaleX = if (minimized) 0.5f else 1f; monster.scaleY = monster.scaleX
            minBtn.text = if (minimized) "+" else "–"
            if (minimized) bubble.visibility = View.GONE
        }
        close.setOnClickListener {
            scope.launch { SettingsStore(this@OverlayService).set(K.OVERLAY, false) }
            stopSelf()
        }

        val store = SettingsStore(this)
        scope.launch { store.flow.collect { prefs = it } }
        scope.launch {
            combine(TrackerState.onSupportedApp, store.flow) { on, p -> on && !p.trackingPaused && p.overlayEnabled }
                .collect { root.visibility = if (it) View.VISIBLE else View.GONE }
        }
        scope.launch { Repo.get(this@OverlayService).todayFlow.collect { monster.setCount(it.total) } }
        scope.launch {
            TrackerState.reactions.collect { r ->
                reactionCount++
                val line = Dialogues.pick(r.total)
                if (!prefs.muted && !minimized) say(line)
                when {
                    Dialogues.isMilestone(r.total) -> monster.celebrate()
                    reactionCount % 3 == 0 -> monster.shakeHead()
                    else -> monster.hop()
                }
            }
        }
        scope.launch {
            TrackerState.welcomeBack.collect { if (!prefs.muted) say(Dialogues.welcome()); monster.celebrate() }
        }
    }

    private fun say(t: String) {
        bubble.text = t; bubble.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ bubble.visibility = View.GONE }, 4500)
    }

    override fun onDestroy() {
        scope.cancel(); handler.removeCallbacksAndMessages(null)
        if (added) try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }
}
