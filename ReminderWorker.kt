package com.reelmonster.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reelmonster.app.MainActivity
import com.reelmonster.app.data.K
import com.reelmonster.app.data.Repo
import com.reelmonster.app.data.SettingsStore
import com.reelmonster.app.monster.Dialogues

/** Runs every ~15 min (Android's minimum for periodic work, so reminders can be up to 15 min late). */
class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val prefs = store.current()
        if (!prefs.remindersOn || prefs.trackingPaused) return Result.success()
        val repo = Repo.get(applicationContext)
        val today = repo.today()
        val stats = repo.todayOnce()
        val minutes = stats.measuredSeconds / 60

        val bucket = if (prefs.remindMinutes > 0) minutes / prefs.remindMinutes else 0
        val lastBucket = if (prefs.lastBucketDate == today) prefs.lastBucket else 0
        if (bucket > lastBucket) {
            store.set(K.LAST_BUCKET, bucket); store.set(K.LAST_BUCKET_DATE, today)
            notify(1, "You've scrolled ${minutes} min today", Dialogues.nudges.random())
        } else if (prefs.lastBucketDate != today) {
            store.set(K.LAST_BUCKET, 0); store.set(K.LAST_BUCKET_DATE, today)
        }
        if (prefs.dailyGoal > 0 && stats.total >= prefs.dailyGoal && prefs.goalNotifiedDate != today) {
            store.set(K.GOAL_NOTIFIED_DATE, today)
            notify(2, "Daily goal reached 🎯", "You hit ${prefs.dailyGoal} reels. Let's call it a day, bestie?")
        }
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String) {
        val c = applicationContext
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && android.os.Build.VERSION.SDK_INT >= 33) return
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("reminders", "Break reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(c, "reminders").setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("👾 $title").setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        NotificationManagerCompat.from(c).notify(id, n)
    }
}
