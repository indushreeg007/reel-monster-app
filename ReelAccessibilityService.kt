package com.reelmonster.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reelmonster.app.data.Prefs
import com.reelmonster.app.data.Repo
import com.reelmonster.app.data.SettingsStore
import com.reelmonster.app.data.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Privacy: only reads the window when the foreground package is Instagram or YouTube. Reads ONLY the
 * view IDs listed in DetectionConfig, hashes their text in memory to tell reels apart, and never
 * stores or transmits that text. Only integer counts and seconds are saved (locally).
 */
class ReelAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var repo: Repo
    @Volatile private var prefs = Prefs()
    @Volatile private var surface: Source? = null

    private var currentSrc: Source? = null
    private var lastRun = 0L
    private var pendingFp: String? = null
    private var pendingSince = 0L
    private var lastCounted: String? = null
    private val recent = ArrayDeque<String>()
    private var unsavedSeconds = 0

    private val recheck = Runnable { currentSrc?.let { evaluate(it) } }

    companion object {
        const val DWELL_MS = 1000L        // a reel must be on screen this long before it is counted
        const val THROTTLE_MS = 250L      // collapse event bursts
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = Repo.get(this)
        val store = SettingsStore(this)
        TrackerState.serviceConnected.value = true
        scope.launch { store.flow.collect { prefs = it } }
        scope.launch {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            while (isActive) {
                delay(1000)
                if (surface != null && !prefs.trackingPaused && pm.isInteractive) unsavedSeconds++
                if (unsavedSeconds >= 10 || (surface == null && unsavedSeconds > 0)) {
                    val s = unsavedSeconds; unsavedSeconds = 0; repo.addSeconds(s)
                }
            }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        TrackerState.serviceConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        TrackerState.serviceConnected.value = false
        TrackerState.onSupportedApp.value = false
        scope.cancel(); super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (prefs.trackingPaused) { TrackerState.status.value = "Tracking paused"; return }

        if (pkg == packageName) {
            // Our own overlay also emits events; only our MainActivity coming forward means "user left the apps".
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                e.className?.toString()?.endsWith("MainActivity") == true) leaveApps()
            return
        }
        val src = when (pkg) { DetectionConfig.IG -> Source.INSTAGRAM; DetectionConfig.YT -> Source.YOUTUBE; else -> null }
        if (src == null) {
            val transient = pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !transient) leaveApps()
            return
        }
        if (!TrackerState.onSupportedApp.value) {
            TrackerState.onSupportedApp.value = true
            val last = TrackerState.lastReelAt
            if (last > 0 && System.currentTimeMillis() - last > 30 * 60_000L) TrackerState.welcomeBack.tryEmit(Unit)
        }
        currentSrc = src
        val now = SystemClock.uptimeMillis()
        if (now - lastRun < THROTTLE_MS) {
            main.removeCallbacks(recheck); main.postDelayed(recheck, THROTTLE_MS + 50); return
        }
        lastRun = now
        evaluate(src)
    }

    private fun leaveApps() {
        TrackerState.onSupportedApp.value = false
        TrackerState.status.value = "Waiting for Instagram or YouTube"
        surface = null; currentSrc = null; pendingFp = null; lastCounted = null; recent.clear()
        main.removeCallbacks(recheck)
    }

    private fun evaluate(src: Source) {
        val root = rootInActiveWindow ?: return
        val cfg = DetectionConfig.of(src)
        if (root.packageName?.toString() != cfg.pkg) return

        val onSurface = cfg.surfaceIds.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
        if (!onSurface) {
            surface = null; pendingFp = null; lastCounted = null
            TrackerState.status.value = "Not on ${cfg.label} (feed, profile and other screens are ignored)"
            return
        }
        surface = src
        if (cfg.blockerIds.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
            TrackerState.status.value = "Comments/share open: counting paused"; return
        }
        val fp = fingerprint(root, cfg)
        if (fp.isEmpty()) {
            TrackerState.status.value = "${cfg.label} screen found, but this app version hides what I need to tell reels apart. Not counting (see README)."
            return
        }
        TrackerState.status.value = "Watching ${cfg.label} ✓"
        if (fp == lastCounted) { pendingFp = null; return }

        val now = SystemClock.uptimeMillis()
        if (fp != pendingFp) {
            pendingFp = fp; pendingSince = now
            main.removeCallbacks(recheck); main.postDelayed(recheck, DWELL_MS + 80)
            return
        }
        if (now - pendingSince >= DWELL_MS) {
            lastCounted = fp; pendingFp = null
            if (fp in recent) return                 // scrolled back to a reel already counted
            recent.addLast(fp); while (recent.size > 8) recent.removeFirst()
            scope.launch {
                val total = repo.addReel(src)
                TrackerState.lastReelAt = System.currentTimeMillis()
                TrackerState.reactions.tryEmit(Reaction(total, src))
            }
        } else {
            main.removeCallbacks(recheck); main.postDelayed(recheck, DWELL_MS - (now - pendingSince) + 50)
        }
    }

    private fun fingerprint(root: android.view.accessibility.AccessibilityNodeInfo, cfg: AppCfg): String {
        val sb = StringBuilder()
        for (id in cfg.fieldIds) for (n in root.findAccessibilityNodeInfosByViewId(id)) {
            n.text?.let { sb.append(it) }; n.contentDescription?.let { sb.append(it) }
        }
        return if (sb.isEmpty()) "" else sb.toString().hashCode().toString()
    }
}
