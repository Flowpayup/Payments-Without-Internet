package com.flowpay.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot events that flow from [com.flowpay.app.MainActivity] (which owns the
 * ActivityResult launchers and the business-logic helper) up to `MainScreen`
 * (which owns the Compose UI state). This replaces the static `@Volatile`
 * callback fields that used to live on MainActivity's companion object — a
 * shared ViewModel, resolved to the same instance from both the Activity
 * (`by viewModels()`) and the composable (`viewModel()`), is the idiomatic,
 * lifecycle-safe bridge.
 */
sealed interface MainUiEvent {
    /** The QR scanner activity returned; clear the "Opening…" button state. */
    data object QrScannerClosed : MainUiEvent

    /** A payment needs overlay permission; show the explanation dialog. */
    data object OverlayPermissionNeeded : MainUiEvent
}

class MainViewModel : ViewModel() {

    // extraBufferCapacity lets tryEmit succeed without a collector attached yet
    // (e.g. an event fired during a configuration change before recomposition).
    private val _events = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainUiEvent> = _events.asSharedFlow()

    fun onQrScannerClosed() {
        _events.tryEmit(MainUiEvent.QrScannerClosed)
    }

    fun onOverlayPermissionNeeded() {
        _events.tryEmit(MainUiEvent.OverlayPermissionNeeded)
    }
}
