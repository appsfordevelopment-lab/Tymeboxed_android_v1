package dev.ambitionsoftware.tymeboxed.ui.screens.inapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppToggleKeys
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class InAppBlockingViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
) : ViewModel() {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(InAppUiState.from(app))
    val state: StateFlow<InAppUiState> = _state.asStateFlow()

    /**
     * Persist a toggle and update the UI state from the *same* in-memory
     * model — not by re-reading [SharedPreferences].
     *
     * The previous implementation called `prefs.edit { ... }` (which writes
     * to memory + schedules an async commit to disk) and then immediately
     * re-loaded from `InAppUiState.from(app)`. On heavily-loaded devices
     * the rebuilt state could read the *previous* value before the in-memory
     * write became visible to a subsequent `getBoolean()` call, producing a
     * visible flicker / lost toggle (audit #24: SharedPreferences race).
     *
     * Now we (1) update the UI state directly from the requested value, and
     * (2) flush the write on a background dispatcher so the commit is
     * durable but the UI never blocks on it.
     */
    fun setToggle(key: String, value: Boolean) = viewModelScope.launch {
        _state.value = _state.value.withToggle(key, value)
        withContext(Dispatchers.IO) {
            // commit() is synchronous on the IO dispatcher → next reader
            // (e.g. the accessibility service) sees the new value immediately.
            prefs.edit().putBoolean(key, value).commit()
        }
    }

    companion object {
        private const val PREFS = "tymeboxed_inapp_toggles"
    }
}

data class InAppUiState(
    /** When true, per-app toggles apply without an active focus session. */
    val blockInAppWithoutSession: Boolean,
    val blockYtShorts: Boolean,
    val blockYtSearch: Boolean,
    val blockYtComments: Boolean,
    val blockYtPip: Boolean,
    val blockIgReels: Boolean,
    val blockIgExplore: Boolean,
    val blockIgSearch: Boolean,
    val blockIgStories: Boolean,
    val blockIgComments: Boolean,
    val blockXHome: Boolean,
    val blockXSearch: Boolean,
    val blockXGrok: Boolean,
    val blockXNotifications: Boolean,
    val blockSnapMap: Boolean,
    val blockSnapStories: Boolean,
    val blockSnapSpotlight: Boolean,
    val blockSnapFollowing: Boolean,
) {
    /** Returns a new state with one toggle changed without touching disk. */
    fun withToggle(key: String, value: Boolean): InAppUiState = when (key) {
        InAppToggleKeys.KEY_BLOCK_INAPP -> copy(blockInAppWithoutSession = value)
        InAppToggleKeys.KEY_BLOCK_YT_SHORTS -> copy(blockYtShorts = value)
        InAppToggleKeys.KEY_BLOCK_YT_SEARCH -> copy(blockYtSearch = value)
        InAppToggleKeys.KEY_BLOCK_YT_COMMENTS -> copy(blockYtComments = value)
        InAppToggleKeys.KEY_BLOCK_YT_PIP -> copy(blockYtPip = value)
        InAppToggleKeys.KEY_BLOCK_IG_REELS -> copy(blockIgReels = value)
        InAppToggleKeys.KEY_BLOCK_IG_EXPLORE -> copy(blockIgExplore = value)
        InAppToggleKeys.KEY_BLOCK_IG_SEARCH -> copy(blockIgSearch = value)
        InAppToggleKeys.KEY_BLOCK_IG_STORIES -> copy(blockIgStories = value)
        InAppToggleKeys.KEY_BLOCK_IG_COMMENTS -> copy(blockIgComments = value)
        InAppToggleKeys.KEY_BLOCK_X_HOME -> copy(blockXHome = value)
        InAppToggleKeys.KEY_BLOCK_X_SEARCH -> copy(blockXSearch = value)
        InAppToggleKeys.KEY_BLOCK_X_GROK -> copy(blockXGrok = value)
        InAppToggleKeys.KEY_BLOCK_X_NOTIFICATIONS -> copy(blockXNotifications = value)
        InAppToggleKeys.KEY_BLOCK_SNAP_MAP -> copy(blockSnapMap = value)
        InAppToggleKeys.KEY_BLOCK_SNAP_STORIES -> copy(blockSnapStories = value)
        InAppToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT -> copy(blockSnapSpotlight = value)
        InAppToggleKeys.KEY_BLOCK_SNAP_FOLLOWING -> copy(blockSnapFollowing = value)
        else -> this
    }

    companion object {
        fun from(ctx: android.content.Context): InAppUiState {
            val p = ctx.getSharedPreferences("tymeboxed_inapp_toggles", android.content.Context.MODE_PRIVATE)
            return InAppUiState(
                blockInAppWithoutSession = p.getBoolean(InAppToggleKeys.KEY_BLOCK_INAPP, false),
                blockYtShorts = p.getBoolean(InAppToggleKeys.KEY_BLOCK_YT_SHORTS, false),
                blockYtSearch = p.getBoolean(InAppToggleKeys.KEY_BLOCK_YT_SEARCH, false),
                blockYtComments = p.getBoolean(InAppToggleKeys.KEY_BLOCK_YT_COMMENTS, false),
                blockYtPip = p.getBoolean(InAppToggleKeys.KEY_BLOCK_YT_PIP, false),
                blockIgReels = p.getBoolean(InAppToggleKeys.KEY_BLOCK_IG_REELS, false),
                blockIgExplore = p.getBoolean(InAppToggleKeys.KEY_BLOCK_IG_EXPLORE, false),
                blockIgSearch = p.getBoolean(InAppToggleKeys.KEY_BLOCK_IG_SEARCH, false),
                blockIgStories = p.getBoolean(InAppToggleKeys.KEY_BLOCK_IG_STORIES, false),
                blockIgComments = p.getBoolean(InAppToggleKeys.KEY_BLOCK_IG_COMMENTS, false),
                blockXHome = p.getBoolean(InAppToggleKeys.KEY_BLOCK_X_HOME, false),
                blockXSearch = p.getBoolean(InAppToggleKeys.KEY_BLOCK_X_SEARCH, false),
                blockXGrok = p.getBoolean(InAppToggleKeys.KEY_BLOCK_X_GROK, false),
                blockXNotifications = p.getBoolean(InAppToggleKeys.KEY_BLOCK_X_NOTIFICATIONS, false),
                blockSnapMap = p.getBoolean(InAppToggleKeys.KEY_BLOCK_SNAP_MAP, false),
                blockSnapStories = p.getBoolean(InAppToggleKeys.KEY_BLOCK_SNAP_STORIES, false),
                blockSnapSpotlight = p.getBoolean(InAppToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT, false),
                blockSnapFollowing = p.getBoolean(InAppToggleKeys.KEY_BLOCK_SNAP_FOLLOWING, false),
            )
        }
    }
}
