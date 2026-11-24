package com.reelmonster.app.service

import com.reelmonster.app.data.Source

/**
 * ===== THE ONLY FILE YOU SHOULD NEED TO EDIT WHEN INSTAGRAM / YOUTUBE UPDATE =====
 *
 * These are view-ID names the apps used when this was written. They are best-effort and UNVERIFIED
 * against your installed app versions: both apps change them without notice (and may obfuscate them).
 * To find the right ones: open a Reel/Short, then run
 *     adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
 * (or use Android Studio > Layout Inspector) and look at resource-id values.
 *
 * surfaceIds : IDs that exist ONLY on the full-screen Reels/Shorts pager (not on feed, profile, etc.)
 * fieldIds   : IDs of per-reel text (author, caption...). Their text is hashed in memory to tell reels apart.
 * blockerIds : IDs present while comments/share sheets are open (counting is paused then).
 */
data class AppCfg(
    val pkg: String, val label: String,
    val surfaceIds: List<String>, val fieldIds: List<String>, val blockerIds: List<String>
)

object DetectionConfig {
    const val IG = "com.instagram.android"
    const val YT = "com.google.android.youtube"

    val instagram = AppCfg(
        IG, "Instagram Reels",
        surfaceIds = listOf("$IG:id/clips_viewer_view_pager", "$IG:id/clips_viewer_fragment_container"),
        fieldIds = listOf("$IG:id/clips_author_username", "$IG:id/clips_caption_component", "$IG:id/clips_caption"),
        blockerIds = listOf("$IG:id/layout_comment_thread_container", "$IG:id/bottom_sheet_container")
    )
    val youtube = AppCfg(
        YT, "YouTube Shorts",
        surfaceIds = listOf("$YT:id/reel_recycler", "$YT:id/reel_player_page_container"),
        fieldIds = listOf("$YT:id/reel_channel_bar", "$YT:id/reel_dyn_title", "$YT:id/shorts_title"),
        blockerIds = listOf("$YT:id/engagement_panel_content", "$YT:id/comment_composer")
    )
    fun of(s: Source) = if (s == Source.INSTAGRAM) instagram else youtube
}
