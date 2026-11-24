# Reel Monster 👾 (Android, Kotlin + Jetpack Compose)

**Status: source code only. Not compiled and not tested on a device.** Build it, install it, and treat each
feature as unverified until you have seen it work on your phone (checklist below).

## Build (Android Studio, easiest)
1. Install Android Studio (Koala or newer). Open the `ReelMonster` folder. It downloads Gradle + dependencies
   and generates the Gradle wrapper on first sync (needs internet).
2. Plug in a phone (USB debugging on) or use a **real device**; emulators don't have Instagram with Reels.
3. **Debug APK:** Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s)
   → `app/build/outputs/apk/debug/app-debug.apk`
4. **Release APK:** Build ▸ Generate Signed App Bundle/APK ▸ APK, using your own keystore.
   (The Gradle file signs `release` with the debug key so `./gradlew assembleRelease` works for personal sideloading.
   Don't publish that.) Output: `app/build/outputs/apk/release/`.

Command line, once the wrapper exists: `./gradlew assembleDebug` / `./gradlew assembleRelease`.

## Install & enable on the phone
1. Copy the APK over and install (allow "install unknown apps").
2. Open Reel Monster, finish onboarding.
3. **Display over other apps** → allow.
4. **Accessibility** → Installed/Downloaded apps → Reel Monster → On.
   On Android 13+ sideloaded apps get "restricted settings": Settings ▸ Apps ▸ Reel Monster ▸ ⋮ ▸ *Allow restricted settings*, then retry.
5. Allow notifications (for reminders).
6. Open Instagram Reels / YouTube Shorts. The monster appears; drag it, tap it for stats, – minimizes, ✕ hides (re-enable on Home).

## ⚠️ The most important caveat: reel detection
Android gives no official "a new reel started" signal. This app reads Instagram's/YouTube's internal view IDs via the
Accessibility API. The IDs in `service/DetectionConfig.kt` are **best-effort and unverified** for your installed versions,
and both apps change them regularly (or may hide them). When they don't match, the Home screen shows an honest status
("screen found but can't tell reels apart"; "Not on Reels") and **nothing is counted**. Use the manual +/- on the Today screen
meanwhile.

To fix detection for your version:
1. Open a Reel. `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml`
   (or Android Studio ▸ Layout Inspector / Tools ▸ Device Explorer).
2. Find `resource-id`s: one that exists only in the full-screen reels pager (→ `surfaceIds`), ones carrying author/caption text (→ `fieldIds`),
   and ones present when the comments sheet is open (→ `blockerIds`).
3. Edit `DetectionConfig.kt`, rebuild.

How counting avoids duplicates: events are throttled (250 ms); a reel must be on screen and unchanged for 1 s; the same reel is never
counted twice in a row; scrolling back to a recently counted reel isn't recounted; comments/share sheets pause counting;
non-Reels screens aren't counted.

## Manual test checklist (please actually run these)
- [ ] Overlay appears only over Instagram/YouTube, hides on home screen and inside Reel Monster itself
- [ ] Drag works; tap opens Today; – and ✕ work; doesn't block Instagram buttons
- [ ] Open Reels: status says "Watching Instagram Reels ✓"; swipe 3 reels → count 3 (not 1 or 6)
- [ ] Open comments, scroll them: count doesn't change
- [ ] Open the feed / a profile: nothing counts
- [ ] Repeat on YouTube Shorts
- [ ] Count survives force-stopping the app; day rollover creates a new row (set phone date past midnight to test)
- [ ] Weekly screen shows real data after a few days
- [ ] Reminder notification fires (set 15 min interval; WorkManager can be delayed by battery saver)
- [ ] Battery optimisation: if the overlay/service dies, exempt Reel Monster from battery optimisation on aggressive OEMs (Xiaomi, Oppo, Samsung…)

## Architecture
```
data/     Room (DayStats per date), Repo, DataStore settings
service/  ReelAccessibilityService (detection), DetectionConfig, OverlayService (foreground), ReminderWorker (WorkManager)
monster/  MonsterView (Canvas-drawn, animated), growth stages, Dialogues
ui/       Compose screens: splash, onboarding, permissions, home, today, week, collection, settings
```
Midnight reset: stats are keyed by date, so a new day starts a fresh row and history is untouched; the UI re-checks the date every 20 s.

## Known limitations / honest notes
- Time "measured" = time the Reels/Shorts screen was detected with screen on; it can't see if you're paused or looking away. "Estimated" = count × your per-reel seconds setting.
- Break reminders run via WorkManager (~15 min granularity, may be delayed by Doze).
- Monster speech is text-only; "Mute" turns off speech bubbles. No sound effects are included.
- The speech bubble briefly makes a transparent touch area around the monster (4.5 s).
- The overlay is started from the app UI (and by its foreground service); it is not auto-started on boot.
- Sideloaded accessibility apps may be disabled by some OEM battery managers.
- Google Play's accessibility policy is strict; this is intended for personal sideloading.
