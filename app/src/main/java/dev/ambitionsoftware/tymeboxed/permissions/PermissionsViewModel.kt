package dev.ambitionsoftware.tymeboxed.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for the intro wizard's permissions step, the standalone
 * [PermissionsScreen], and the Settings → Permissions card. Wrapping
 * [PermissionsCoordinator] here means there's exactly one place that calls
 * `refresh()` on app resume, and the three UI surfaces stay in sync.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val coordinator: PermissionsCoordinator,
) : ViewModel() {

    /**
     * Same backing map as [PermissionsCoordinator] — do not wrap in a second [stateIn] with
     * `emptyMap()` or the home screen briefly thinks every permission is missing.
     */
    val states: StateFlow<Map<TymePermission, Boolean>> = coordinator.states

    /** Kept in sync with [states] inside [PermissionsCoordinator.refresh] (single atomic update). */
    val allRequiredGranted: StateFlow<Boolean> = coordinator.allRequiredGranted

    /** True when the device has NFC hardware. */
    val isNfcAvailable: Boolean get() = coordinator.isNfcHardwareAvailable

    /** Call from `ON_RESUME` so the settings deep-link updates re-check. */
    fun refresh() = coordinator.refresh()

    /**
     * Some devices apply accessibility / usage-access changes a few hundred ms after returning
     * from the system Settings screen — a single immediate [refresh] still sees the old state.
     */
    fun refreshAfterReturningFromSettings() {
        refresh()
        viewModelScope.launch {
            delay(450L)
            refresh()
        }
    }

    init {
        // Process restarts / first frame: align with Settings before any lifecycle ON_RESUME.
        coordinator.refresh()
    }
}
