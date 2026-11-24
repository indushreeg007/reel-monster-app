package com.reelmonster.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reelmonster.app.data.*
import com.reelmonster.app.service.ReelAccessibilityService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Perms(val overlay: Boolean, val accessibility: Boolean, val notifications: Boolean)

class AppVm(app: Application) : AndroidViewModel(app) {
    val repo = Repo.get(app)
    private val store = SettingsStore(app)
    val today = repo.todayFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DayStats(repo.today()))
    val week = repo.weekFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val record = repo.record.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val prefs = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    val perms = MutableStateFlow(readPerms())

    private fun readPerms(): Perms {
        val c = getApplication<Application>()
        val flat = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val me = ComponentName(c, ReelAccessibilityService::class.java).flattenToString()
        val notif = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return Perms(Settings.canDrawOverlays(c), flat.split(':').any { it.equals(me, true) }, notif)
    }
    fun refreshPerms() { perms.value = readPerms() }
    fun <T> set(k: Preferences.Key<T>, v: T) { viewModelScope.launch { store.set(k, v) } }
    fun adjust(s: Source, d: Int) { viewModelScope.launch { repo.adjust(s, d) } }
    fun wipe() { viewModelScope.launch { repo.clearAll() } }
}
