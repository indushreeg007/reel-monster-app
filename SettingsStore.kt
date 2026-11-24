package com.reelmonster.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("settings")

object K {
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val MUTED = booleanPreferencesKey("muted")
    val PAUSED = booleanPreferencesKey("tracking_paused")
    val OVERLAY = booleanPreferencesKey("overlay_enabled")
    val REMINDERS = booleanPreferencesKey("reminders_on")
    val REMIND_MIN = intPreferencesKey("remind_minutes")
    val GOAL = intPreferencesKey("daily_goal")
    val EST_SEC = intPreferencesKey("est_seconds_per_reel")
    val LAST_BUCKET = intPreferencesKey("last_bucket")
    val LAST_BUCKET_DATE = stringPreferencesKey("last_bucket_date")
    val GOAL_NOTIFIED_DATE = stringPreferencesKey("goal_notified_date")
}

data class Prefs(
    val onboarded: Boolean = false, val muted: Boolean = false, val trackingPaused: Boolean = false,
    val overlayEnabled: Boolean = true, val remindersOn: Boolean = true, val remindMinutes: Int = 30,
    val dailyGoal: Int = 50, val estSecondsPerReel: Int = 25,
    val lastBucket: Int = 0, val lastBucketDate: String = "", val goalNotifiedDate: String = ""
)

class SettingsStore(private val ctx: Context) {
    val flow: Flow<Prefs> = ctx.ds.data.map { p ->
        Prefs(
            p[K.ONBOARDED] ?: false, p[K.MUTED] ?: false, p[K.PAUSED] ?: false, p[K.OVERLAY] ?: true,
            p[K.REMINDERS] ?: true, p[K.REMIND_MIN] ?: 30, p[K.GOAL] ?: 50, p[K.EST_SEC] ?: 25,
            p[K.LAST_BUCKET] ?: 0, p[K.LAST_BUCKET_DATE] ?: "", p[K.GOAL_NOTIFIED_DATE] ?: ""
        )
    }
    suspend fun current(): Prefs = flow.first()
    suspend fun <T> set(key: Preferences.Key<T>, v: T) { ctx.ds.edit { it[key] = v } }
}
