package com.reelmonster.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.reelmonster.app.service.ReminderWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // The worker itself checks the user's reminder settings, so it is always scheduled.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "break_reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
