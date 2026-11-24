package com.reelmonster.app.service

import com.reelmonster.app.data.Source
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class Reaction(val total: Int, val source: Source)

/** In-process bridge between the accessibility service and the overlay/UI. Holds no personal content. */
object TrackerState {
    val serviceConnected = MutableStateFlow(false)
    val onSupportedApp = MutableStateFlow(false)
    val status = MutableStateFlow("Waiting for Instagram or YouTube")
    val reactions = MutableSharedFlow<Reaction>(extraBufferCapacity = 8)
    val welcomeBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @Volatile var lastReelAt = 0L   // wall-clock ms
}
