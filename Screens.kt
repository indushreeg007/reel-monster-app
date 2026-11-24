package com.reelmonster.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reelmonster.app.data.K
import com.reelmonster.app.data.Source
import com.reelmonster.app.monster.MonsterView
import com.reelmonster.app.monster.Stage
import com.reelmonster.app.monster.moodFor
import com.reelmonster.app.service.OverlayService
import com.reelmonster.app.service.TrackerState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val TABS = listOf("home" to "🏠 Home", "today" to "📊 Today", "week" to "📅 Week", "monsters" to "👾 Monsters", "settings" to "⚙️ Settings")

@Composable
fun AppRoot(vm: AppVm, external: String?, consumed: () -> Unit) {
    var screen by remember { mutableStateOf("splash") }
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val perms by vm.perms.collectAsState()

    LaunchedEffect(Unit) { delay(1500); if (screen == "splash") screen = if (vm.prefs.value.onboarded) "home" else "onboarding" }
    LaunchedEffect(external) { if (external != null && screen != "splash") { screen = external; consumed() } }
    // Start/stop the floating monster to match the setting + permission.
    LaunchedEffect(prefs.overlayEnabled, perms.overlay) {
        val i = Intent(ctx, OverlayService::class.java)
        if (prefs.overlayEnabled && perms.overlay) ctx.startForegroundService(i) else ctx.stopService(i)
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            if (screen in TABS.map { it.first }) NavigationBar(containerColor = Color.White) {
                TABS.forEach { (r, l) ->
                    NavigationBarItem(
                        selected = screen == r, onClick = { screen = r },
                        icon = { Text(l.substringBefore(' '), fontSize = 20.sp) },
                        label = { Text(l.substringAfter(' '), fontSize = 10.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                "splash" -> Splash()
                "onboarding" -> Onboarding { screen = "permissions" }
                "permissions" -> PermissionSetup(vm) { vm.set(K.ONBOARDED, true); screen = "home" }
                "home" -> Home(vm) { screen = it }
                "today" -> Today(vm)
                "week" -> Week(vm)
                "monsters" -> Collection(vm)
                "settings" -> SettingsScreen(vm) { screen = "onboarding" }
            }
        }
    }
}

@Composable fun Monster(count: Int, modifier: Modifier = Modifier) = AndroidView(
    factory = { MonsterView(it).apply { setCount(count, false) } },
    update = { it.setCount(count, true) }, modifier = modifier
)

@Composable fun AnimNum(n: Int, size: Int = 40) {
    val v by animateIntAsState(n, tween(800), label = "num")
    Text(v.toString(), fontSize = size.sp, fontWeight = FontWeight.Black)
}

fun fmt(sec: Int): String = if (sec < 60) "${sec}s" else if (sec < 3600) "${sec / 60}m ${sec % 60}s" else "${sec / 3600}h ${(sec % 3600) / 60}m"

@Composable fun Title(t: String, sub: String? = null) {
    Text(t, fontSize = 28.sp, fontWeight = FontWeight.Black)
    if (sub != null) Text(sub, fontSize = 13.sp, color = Ink.copy(alpha = .7f))
    Spacer(Modifier.height(12.dp))
}

@Composable fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable fun Tile(emoji: String, label: String, color: Color, modifier: Modifier, value: @Composable () -> Unit) =
    SoftCard(modifier, color) { Text(emoji, fontSize = 22.sp); value(); Text(label, fontSize = 12.sp, color = Ink.copy(alpha = .75f)) }

// ---------- Splash / onboarding / permissions ----------
@Composable fun Splash() {
    val s by rememberInfiniteTransition(label = "s").animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "sc")
    Column(Modifier.fillMaxSize().background(Lavender.copy(alpha = .4f)), Arrangement.Center, Alignment.CenterHorizontally) {
        Monster(0, Modifier.scale(s * 1.6f))
        Spacer(Modifier.height(40.dp))
        Text("REEL MONSTER", fontSize = 32.sp, fontWeight = FontWeight.Black, color = SoftPurple)
        Text("scroll less, grow less 👾", fontSize = 14.sp)
    }
}

@Composable fun Onboarding(next: () -> Unit) {
    val pages = listOf(
        Triple("👾", "Meet your Reel Monster", "A tiny floating monster that sits on top of Instagram and YouTube while you watch Reels and Shorts."),
        Triple("🍼", "It grows as you scroll", "Every new reel feeds it. Tiny baby → giant exhausted monster. It will absolutely comment on this."),
        Triple("🔒", "Private by design", "Everything stays on your phone. No account, no server. It only notices WHEN a new reel appears. It never reads messages or passwords.")
    )
    var i by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(pages[i].first, fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(pages[i].second, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(pages[i].third, textAlign = TextAlign.Center, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { if (i < pages.lastIndex) i++ else next() }, shape = RoundedCornerShape(20.dp)) {
            Text(if (i < pages.lastIndex) "Next" else "Set up permissions")
        }
    }
}

@Composable fun PermissionSetup(vm: AppVm, done: () -> Unit) {
    val ctx = LocalContext.current
    val p by vm.perms.collectAsState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPerms() }
    Page {
        Title("Permissions", "Each one is optional to grant, but the monster needs them to work.")
        PermRow("🫧 Display over other apps", "Lets the monster float on top of Instagram/YouTube.", p.overlay, "Allow") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermRow("👀 Accessibility service", "Lets the app notice when a new Reel/Short appears. Only Instagram and YouTube are read; nothing leaves your phone.", p.accessibility, "Open settings") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        SoftCard(color = Peach.copy(alpha = .5f)) {
            Text("Heads-up (Android 13+): if the accessibility toggle is greyed out, open Settings → Apps → Reel Monster → ⋮ menu → \"Allow restricted settings\", then try again. Then find Reel Monster under Installed/Downloaded apps and turn it on.", fontSize = 13.sp)
        }
        PermRow("🔔 Notifications", "For optional break reminders.", p.notifications, "Allow") {
            if (android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Button(onClick = done, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Text("Done") }
    }
}

@Composable fun PermRow(title: String, why: String, granted: Boolean, btn: String, act: () -> Unit) =
    SoftCard(color = if (granted) PastelBlue.copy(alpha = .5f) else Color.White) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(why, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (granted) Text("✅ Enabled", fontWeight = FontWeight.Bold) else Button(onClick = act, shape = RoundedCornerShape(16.dp)) { Text(btn) }
    }

// ---------- Home ----------
@Composable fun Home(vm: AppVm, go: (String) -> Unit) {
    val t by vm.today.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val perms by vm.perms.collectAsState()
    val status by TrackerState.status.collectAsState()
    val connected by TrackerState.serviceConnected.collectAsState()
    Page {
        Title("Reel Monster 👾", "Today, ${LocalDate.now()}")
        SoftCard(Modifier.fillMaxWidth(), Lavender.copy(alpha = .45f)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Monster(t.total)
                AnimNum(t.total, 56)
                Text("reels today", fontSize = 14.sp)
                Text(Stage.of(t.total).label, fontWeight = FontWeight.Bold)
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Detection status", fontWeight = FontWeight.Bold)
            Text(
                when {
                    prefs.trackingPaused -> "⏸ Tracking is paused"
                    !perms.accessibility -> "❌ Accessibility service is off. Counting can't work yet."
                    !connected -> "⏳ Service enabled but not connected yet. Try toggling it off/on."
                    else -> status
                }, fontSize = 13.sp
            )
        }
        if (!perms.overlay || !perms.accessibility) Button(onClick = { go("settings") }, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Text("Fix permissions") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.set(K.OVERLAY, !prefs.overlayEnabled) }, Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Text(if (prefs.overlayEnabled) "Hide monster" else "Show monster")
            }
            OutlinedButton(onClick = { vm.set(K.PAUSED, !prefs.trackingPaused) }, Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Text(if (prefs.trackingPaused) "Resume" else "Pause tracking")
            }
        }
    }
}

// ---------- Today ----------
@Composable fun Today(vm: AppVm) {
    val t by vm.today.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val est = t.total * prefs.estSecondsPerReel
    val avgMeasured = if (t.total > 0) t.measuredSeconds / t.total else 0
    Page {
        Title("Today's activity")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("🎞️", "Reels + Shorts today", Pink.copy(alpha = .6f), Modifier.weight(1f)) { AnimNum(t.total) }
            Tile("🌱", "Growth stage", PastelBlue.copy(alpha = .6f), Modifier.weight(1f)) { Text(Stage.of(t.total).label, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("📸", "Instagram Reels", Peach.copy(alpha = .6f), Modifier.weight(1f)) { AnimNum(t.instagram) }
            Tile("▶️", "YouTube Shorts", Lavender.copy(alpha = .6f), Modifier.weight(1f)) { AnimNum(t.youtube) }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("⏱ Time", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Measured: ${fmt(t.measuredSeconds)}", fontWeight = FontWeight.Bold)
            Text("Actual time the Reels/Shorts screen was open with your screen on.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("Estimated: ${fmt(est)}", fontWeight = FontWeight.Bold)
            Text("Reels counted × ${prefs.estSecondsPerReel}s (adjust in Settings). A guess, not a measurement.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("Average per reel (measured): ${if (t.total > 0) fmt(avgMeasured) else "–"}")
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("✏️ Fix the count", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Detection can miss or double-count. Adjust here.", fontSize = 12.sp)
            Fix("Instagram", t.instagram) { vm.adjust(Source.INSTAGRAM, it) }
            Fix("YouTube", t.youtube) { vm.adjust(Source.YOUTUBE, it) }
        }
    }
}

@Composable fun Fix(label: String, n: Int, f: (Int) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("$label: $n", Modifier.weight(1f))
    OutlinedButton(onClick = { f(-1) }, shape = RoundedCornerShape(14.dp)) { Text("−1") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = { f(1) }, shape = RoundedCornerShape(14.dp)) { Text("+1") }
}

// ---------- Week ----------
@Composable fun Week(vm: AppVm) {
    val w by vm.week.collectAsState()
    val record by vm.record.collectAsState()
    val total = w.sumOf { it.total }
    val secs = w.sumOf { it.measuredSeconds }
    val worst = w.maxByOrNull { it.total }?.takeIf { it.total > 0 }
    fun dn(d: String) = LocalDate.parse(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    Page {
        Title("Last 7 days", "Real stored counts. Days with no data show 0.")
        SoftCard(Modifier.fillMaxWidth()) {
            val max = (w.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val slot = size.width / 7f
                w.forEachIndexed { i, d ->
                    val h = (size.height - 8f) * d.total / max
                    drawRoundRect(
                        if (d == worst) SoftPurple else Pink,
                        Offset(i * slot + slot * 0.2f, size.height - h), Size(slot * 0.6f, h.coerceAtLeast(4f)), CornerRadius(16f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) { w.forEach { Text(dn(it.date), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp) } }
            Row(Modifier.fillMaxWidth()) { w.forEach { Text("${it.total}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("🎞️", "Total reels (7d)", Peach.copy(alpha = .6f), Modifier.weight(1f)) { AnimNum(total, 30) }
            Tile("⏱", "Measured time (7d)", PastelBlue.copy(alpha = .6f), Modifier.weight(1f)) { Text(fmt(secs), fontSize = 22.sp, fontWeight = FontWeight.Black) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("🔥", "Most addictive day", Pink.copy(alpha = .6f), Modifier.weight(1f)) { Text(worst?.let { "${dn(it.date)} (${it.total})" } ?: "–", fontSize = 20.sp, fontWeight = FontWeight.Black) }
            Tile("📈", "Daily average", Lavender.copy(alpha = .6f), Modifier.weight(1f)) { Text("${total / 7}", fontSize = 24.sp, fontWeight = FontWeight.Black) }
        }
        Tile("🏆", "Highest scrolling record (all time)", Peach.copy(alpha = .5f), Modifier.fillMaxWidth()) { AnimNum(record, 30) }
    }
}

// ---------- Collection ----------
@Composable fun Collection(vm: AppVm) {
    val t by vm.today.collectAsState()
    val cur = Stage.of(t.total)
    Page {
        Title("Monster collection", "Each stage and its mood. Yours today is highlighted.")
        Stage.values().forEach { s ->
            SoftCard(Modifier.fillMaxWidth(), if (s == cur) Lavender.copy(alpha = .5f) else Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monster(s.min + 2)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(s.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val hi = Stage.values().getOrNull(s.ordinal + 1)?.min?.minus(1)
                        Text(if (hi != null) "${s.min}–$hi reels" else "${s.min}+ reels", fontSize = 13.sp)
                        Text("Mood: ${moodFor(s.min + 2).name.lowercase()}", fontSize = 13.sp)
                        if (s == cur) Text("← you are here", fontWeight = FontWeight.Bold, color = SoftPurple)
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------
@Composable fun SettingsScreen(vm: AppVm, replay: () -> Unit) {
    val ctx = LocalContext.current
    val p by vm.prefs.collectAsState()
    val perms by vm.perms.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPerms() }
    Page {
        Title("Settings")
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Overlay: ${if (perms.overlay) "✅" else "❌"}   Accessibility: ${if (perms.accessibility) "✅" else "❌"}   Notifications: ${if (perms.notifications) "✅" else "❌"}", fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Overlay") }
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                if (!perms.notifications) OutlinedButton(onClick = { if (android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Notif.") }
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Monster", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SwitchRow("Show floating monster", p.overlayEnabled) { vm.set(K.OVERLAY, it) }
            SwitchRow("Mute (no speech bubbles)", p.muted) { vm.set(K.MUTED, it) }
            SwitchRow("Pause tracking", p.trackingPaused) { vm.set(K.PAUSED, it) }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Break reminders", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SwitchRow("Enable reminders", p.remindersOn) { vm.set(K.REMINDERS, it) }
            Text("Remind after this much measured scrolling:", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60).forEach {
                    FilterChip(selected = p.remindMinutes == it, onClick = { vm.set(K.REMIND_MIN, it) }, label = { Text("${it}m") })
                }
            }
            Stepper("Daily goal (reels)", p.dailyGoal, 5, 5, 500) { vm.set(K.GOAL, it) }
            Text("Reminders are checked about every 15 min (Android's minimum).", fontSize = 11.sp)
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Estimates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Stepper("Estimated seconds per reel", p.estSecondsPerReel, 5, 5, 120) { vm.set(K.EST_SEC, it) }
        }
        SoftCard(Modifier.fillMaxWidth(), Lavender.copy(alpha = .35f)) {
            Text("🔒 Privacy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "• The accessibility service receives window events from all apps, but ignores everything except Instagram and YouTube.\n" +
                "• In those apps it only checks for a few Reels/Shorts view IDs and hashes a short text in memory to tell reels apart. That text is never saved or sent anywhere.\n" +
                "• Saved on this phone only: per-day counts and seconds.\n" +
                "• No account, no server, no internet permission in this app.\n" +
                "• Pause tracking above, or revoke the permission in Android settings, any time.", fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = replay) { Text("Replay intro") }
            Spacer(Modifier.height(4.dp))
            if (!confirmWipe) OutlinedButton(onClick = { confirmWipe = true }) { Text("Delete all my stats") }
            else Button(onClick = { vm.wipe(); confirmWipe = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57399))) { Text("Tap again to confirm delete") }
        }
    }
}

@Composable fun SwitchRow(label: String, v: Boolean, f: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(v, f) }

@Composable fun Stepper(label: String, v: Int, step: Int, lo: Int, hi: Int, f: (Int) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $v", Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(onClick = { f((v - step).coerceAtLeast(lo)) }, shape = RoundedCornerShape(12.dp)) { Text("−") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { f((v + step).coerceAtMost(hi)) }, shape = RoundedCornerShape(12.dp)) { Text("+") }
    }
