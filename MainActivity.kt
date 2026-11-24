package com.reelmonster.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelmonster.app.ui.*

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route.value = intent.getStringExtra("route")
        setContent {
            ReelTheme {
                val vm: AppVm = viewModel()
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshPerms() }
                    owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
                }
                AppRoot(vm, route.value) { route.value = null }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route.value = intent.getStringExtra("route")
    }
}
