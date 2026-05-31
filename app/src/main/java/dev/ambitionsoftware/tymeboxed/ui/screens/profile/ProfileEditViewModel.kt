package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.domain.model.ProfileSchedule
import dev.ambitionsoftware.tymeboxed.domain.model.withBreakFlagsFromStrategy
import dev.ambitionsoftware.tymeboxed.data.repository.ProfileRepository
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.service.ActiveSessionLifecycleCoordinator
import dev.ambitionsoftware.tymeboxed.service.BlockingStateRestorer
import dev.ambitionsoftware.tymeboxed.service.SessionReminderScheduler
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
)

data class ProfileEditUiState(
    val isNew: Boolean = true,
    /** `true` once an existing profile has been read from the DB (or immediately for new profiles). */
    val profileReady: Boolean = false,
    val name: String = "",
    val strategyId: String = BlockingStrategyId.DEFAULT,
    val timerMinutes: Int = 25,
    val breakTimeInMinutes: Int = 15,
    val enableStrictMode: Boolean = true,
    val enableLiveActivity: Boolean = false,
    val enableReminder: Boolean = false,
    val reminderTimeMinutes: Int = 15,
    val customReminderMessage: String = "",
    val isAllowMode: Boolean = false,
    val isAllowModeDomains: Boolean = false,
    val blockAdultWebsites: Boolean = true,
    val domains: List<String> = emptyList(),
    val schedule: ProfileSchedule = ProfileSchedule.inactive(),
    val blockedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val deletedSuccessfully: Boolean = false,
    val errorMessage: String? = null,
    /** True when a non-ended session is using this profile — delete must be blocked. */
    val isActiveSessionForThisProfile: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val sessionReminderScheduler: SessionReminderScheduler,
    private val sessionLifecycle: ActiveSessionLifecycleCoordinator,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val profileId: String = savedStateHandle["profileId"] ?: "new"
    private val isNew: Boolean = profileId == "new"

    private val formGson = Gson()

    /**
     * Restore an in-progress form snapshot if the system killed our process
     * while the user was editing. Without this, every process death (background
     * trim, configuration change crash, low-memory kill) would silently throw
     * away the user's unsaved name/toggles/blocked-app selection.
     *
     * We only persist the user-edited subset: heavy / derivable fields
     * (installed apps list, error / saving flags) reload on next launch.
     */
    private val initialPersistedFormState: PersistedFormState? = run {
        val json: String? = savedStateHandle[STATE_KEY_FORM]
        if (json.isNullOrBlank()) null
        else runCatching { formGson.fromJson(json, PersistedFormState::class.java) }.getOrNull()
    }

    private val _state = MutableStateFlow(
        initialPersistedFormState?.applyTo(
            ProfileEditUiState(isNew = isNew, profileReady = isNew),
        ) ?: ProfileEditUiState(isNew = isNew, profileReady = isNew),
    )
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private var loadedProfile: Profile? = null

    private val _pendingNavigationProfileId = MutableStateFlow<String?>(null)
    val pendingNavigationProfileId: StateFlow<String?> = _pendingNavigationProfileId.asStateFlow()

    init {
        loadInstalledApps()
        if (!isNew) {
            // If we have a persisted draft, don't blow it away with the DB
            // copy — that would lose the user's pending edits. We still load
            // [loadedProfile] for the save() merge / active-session check.
            loadProfile(allowOverwriteState = initialPersistedFormState == null)
            viewModelScope.launch {
                sessionRepository.activeSession.collect { session ->
                    val locked = session?.profileId == profileId
                    _state.update { it.copy(isActiveSessionForThisProfile = locked) }
                }
            }
        }

        // Persist user-edited form fields on every change (skip the initial
        // emission so we don't immediately overwrite an existing draft).
        _state
            .drop(1)
            .onEach { snap -> savedStateHandle[STATE_KEY_FORM] = formGson.toJson(PersistedFormState.from(snap)) }
            .launchIn(viewModelScope)
    }

    private fun loadProfile(allowOverwriteState: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.findById(profileId) ?: return@launch
            loadedProfile = profile
            if (!allowOverwriteState) {
                // Persisted draft already populated [_state]; just flip the
                // profileReady flag and exit so we don't clobber unsaved edits.
                _state.update { it.copy(isNew = false, profileReady = true) }
                return@launch
            }
            val timerMinutes = profile.strategyData?.toIntOrNull() ?: 25
            val migratedAppAllowMode = profile.isAllowMode
            _state.update {
                it.copy(
                    isNew = false,
                    profileReady = true,
                    name = profile.name,
                    strategyId = profile.strategyId,
                    timerMinutes = timerMinutes,
                    breakTimeInMinutes = profile.breakTimeInMinutes,
                    enableStrictMode = profile.enableStrictMode,
                    enableLiveActivity = profile.enableLiveActivity,
                    enableReminder = profile.isSessionReminderEnabled(),
                    reminderTimeMinutes = ((profile.reminderTimeSeconds ?: 900) / 60).coerceAtLeast(1),
                    customReminderMessage = profile.customReminderMessage ?: "",
                    isAllowMode = false,
                    isAllowModeDomains = profile.isAllowModeDomains,
                    blockAdultWebsites = profile.blockAdultWebsites,
                    domains = profile.domains,
                    schedule = profile.schedule ?: ProfileSchedule.inactive(),
                    blockedPackages = if (migratedAppAllowMode) emptySet() else profile.blockedPackages.toSet(),
                    errorMessage = if (migratedAppAllowMode) {
                        "Allow-only mode for apps is no longer available. Please select apps to restrict again."
                    } else {
                        it.errorMessage
                    },
                )
            }
        }
    }

    /**
     * Loads all apps that have a launcher intent — this catches user-installed
     * apps AND visible system apps (Calculator, Camera, etc.) that would be
     * useful to block. Falls back to non-system filter if the query fails.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = appContext.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }
                resolveInfos
                    .mapNotNull { it.activityInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != appContext.packageName }
                    .map { info ->
                        InstalledApp(
                            packageName = info.packageName,
                            label = info.loadLabel(pm).toString(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _state.update { it.copy(installedApps = apps) }
        }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name, errorMessage = null) }
    }

    fun onStrategyChange(strategyId: String) {
        _state.update { it.copy(strategyId = strategyId, errorMessage = null) }
    }

    fun onTimerMinutesChange(minutes: Int) {
        _state.update { it.copy(timerMinutes = minutes.coerceIn(1, 480)) }
    }

    fun onBreakTimeChange(minutes: Int) {
        _state.update { it.copy(breakTimeInMinutes = minutes.coerceIn(5, 60)) }
    }

    fun onStrictModeChange(enabled: Boolean) {
        _state.update { it.copy(enableStrictMode = enabled) }
    }

    fun onLiveActivityChange(enabled: Boolean) {
        _state.update { it.copy(enableLiveActivity = enabled) }
    }

    fun onReminderChange(enabled: Boolean) {
        _state.update { it.copy(enableReminder = enabled) }
        if (!enabled) {
            sessionReminderScheduler.cancelAll()
        }
    }

    fun onReminderTimeChange(minutes: Int) {
        _state.update { it.copy(reminderTimeMinutes = minutes.coerceIn(1, 999)) }
    }

    fun onReminderMessageChange(message: String) {
        _state.update { it.copy(customReminderMessage = message.take(178)) }
    }

    fun onAllowModeDomainsChange(enabled: Boolean) {
        _state.update { it.copy(isAllowModeDomains = enabled, domains = emptyList()) }
    }

    fun onBlockAdultWebsitesChange(enabled: Boolean) {
        _state.update { it.copy(blockAdultWebsites = enabled) }
    }

    fun onToggleApp(packageName: String) {
        _state.update { s ->
            val updated = s.blockedPackages.toMutableSet()
            if (updated.contains(packageName)) updated.remove(packageName)
            else updated.add(packageName)
            s.copy(blockedPackages = updated)
        }
    }

    fun onAddDomain(domain: String) {
        val normalized = normalizeDomainInput(domain)
        if (normalized == null) {
            _state.update {
                it.copy(errorMessage = "Enter a valid domain like example.com")
            }
            return
        }
        _state.update { s ->
            if (s.domains.contains(normalized)) {
                s.copy(errorMessage = null)
            } else if (s.domains.size >= MAX_DOMAINS_PER_PROFILE) {
                s.copy(errorMessage = "You can block at most $MAX_DOMAINS_PER_PROFILE domains per profile")
            } else {
                s.copy(domains = s.domains + normalized, errorMessage = null)
            }
        }
    }

    /**
     * Strips scheme / path / `www.` and enforces a conservative RFC-1035-like
     * format. Rejects empty input, oversize labels, leading/trailing dashes,
     * and obvious junk so a stray paste like `"  https://x"` or `"my email"`
     * doesn't enter the block list (audit #25: no domain input validation).
     *
     * Returns the canonical hostname (lower-case, no scheme, no path) when
     * valid, else `null`.
     */
    private fun normalizeDomainInput(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isBlank()) return null
        if (trimmed.length > MAX_DOMAIN_LENGTH) return null

        // Strip scheme + path / query / fragment if the user pasted a URL.
        val noScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .removePrefix("www.")

        if (noScheme.isBlank()) return null
        if (noScheme.length > MAX_DOMAIN_LENGTH) return null
        // Must contain a TLD (a single bare word is never a real domain).
        if (!noScheme.contains('.')) return null
        // Strict label format: letters/digits/hyphens, no leading/trailing hyphen,
        // each label 1..63 chars; allow a trailing dot for FQDNs.
        val core = noScheme.trimEnd('.')
        if (core.isBlank()) return null
        val labelRegex = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
        for (label in core.split('.')) {
            if (!labelRegex.matches(label)) return null
        }
        return core
    }

    fun onRemoveDomain(domain: String) {
        _state.update { s -> s.copy(domains = s.domains - domain) }
    }

    fun updateSchedule(schedule: ProfileSchedule) {
        val normalized = if (schedule.isActive) {
            schedule.copy(updatedAt = System.currentTimeMillis())
        } else {
            ProfileSchedule.inactive()
        }
        _state.update { it.copy(schedule = normalized) }
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Profile name is required") }
            return
        }
        if (current.blockedPackages.isEmpty() &&
            current.domains.isEmpty() &&
            !current.blockAdultWebsites
        ) {
            _state.update { it.copy(errorMessage = "Select at least one app, domain, or adult-site blocking") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val base = loadedProfile ?: Profile.newDraft(current.strategyId)
                val profile = base.copy(
                    name = current.name.trim(),
                    strategyId = current.strategyId,
                    strategyData = if (current.strategyId in listOf(
                            BlockingStrategyId.FOCUS_TIMER,
                            BlockingStrategyId.FOCUS_TIMER_BREAK,
                        )
                    ) {
                        current.timerMinutes.toString()
                    } else {
                        null
                    },
                    enableStrictMode = current.enableStrictMode,
                    enableBreaks = current.strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK,
                    breakTimeInMinutes = current.breakTimeInMinutes,
                    enableLiveActivity = current.enableLiveActivity,
                    reminderTimeSeconds = if (current.enableReminder) {
                        current.reminderTimeMinutes * 60
                    } else null,
                    customReminderMessage = current.customReminderMessage.ifBlank { null },
                    isAllowMode = false,
                    isAllowModeDomains = current.isAllowModeDomains,
                    blockAdultWebsites = current.blockAdultWebsites,
                    domains = current.domains,
                    schedule = current.schedule.takeIf { it.isActive },
                    blockedPackages = current.blockedPackages.toList(),
                )
                profileRepository.save(profile.withBreakFlagsFromStrategy())
                val active = sessionRepository.findActive()
                if (active?.profileId == profile.id) {
                    BlockingStateRestorer.syncLiveActivityForActiveProfile(profile)
                    sessionLifecycle.sync()
                }
                if (!profile.isSessionReminderEnabled()) {
                    sessionReminderScheduler.cancelAll()
                }
                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to save: ${e.localizedMessage}",
                    )
                }
            }
        }
    }

    /**
     * Snapshot for the insights overlay (same profile id as on disk; name reflects the text field).
     */
    fun profileForInsights(): Profile? {
        if (isNew) return null
        val base = loadedProfile ?: return null
        val s = _state.value
        return base.copy(name = s.name.trim().ifBlank { base.name })
    }

    fun consumePendingNavigation() {
        _pendingNavigationProfileId.value = null
    }

    fun duplicateProfile() {
        if (isNew) return
        val current = _state.value
        val base = loadedProfile ?: return
        if (current.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Profile name is required") }
            return
        }
        if (current.blockedPackages.isEmpty() &&
            current.domains.isEmpty() &&
            !current.blockAdultWebsites
        ) {
            _state.update { it.copy(errorMessage = "Select at least one app, domain, or adult-site blocking") }
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val newId = UUID.randomUUID().toString()
                val copy = base.copy(
                    id = newId,
                    name = current.name.trim() + " Copy",
                    createdAt = now,
                    updatedAt = now,
                    strategyId = current.strategyId,
                    strategyData = if (current.strategyId in listOf(
                            BlockingStrategyId.FOCUS_TIMER,
                            BlockingStrategyId.FOCUS_TIMER_BREAK,
                        )
                    ) {
                        current.timerMinutes.toString()
                    } else {
                        null
                    },
                    enableStrictMode = current.enableStrictMode,
                    enableBreaks = current.strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK,
                    breakTimeInMinutes = current.breakTimeInMinutes,
                    enableLiveActivity = current.enableLiveActivity,
                    reminderTimeSeconds = if (current.enableReminder) {
                        current.reminderTimeMinutes * 60
                    } else {
                        null
                    },
                    customReminderMessage = current.customReminderMessage.ifBlank { null },
                    isAllowMode = false,
                    isAllowModeDomains = current.isAllowModeDomains,
                    blockAdultWebsites = current.blockAdultWebsites,
                    domains = current.domains,
                    schedule = current.schedule.takeIf { it.isActive },
                    blockedPackages = current.blockedPackages.toList(),
                )
                profileRepository.save(copy.withBreakFlagsFromStrategy())
                _pendingNavigationProfileId.value = newId
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = "Failed to duplicate: ${e.localizedMessage}")
                }
            }
        }
    }

    fun delete() {
        if (isNew) return
        if (_state.value.isActiveSessionForThisProfile) {
            _state.update {
                it.copy(errorMessage = "End the focus session before deleting this profile.")
            }
            return
        }
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                profileRepository.delete(profileId)
                // Drop the saved draft after a successful delete so a fresh
                // "new" form doesn't inherit it.
                savedStateHandle.remove<String>(STATE_KEY_FORM)
                _state.update { it.copy(isDeleting = false, deletedSuccessfully = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = "Failed to delete: ${e.localizedMessage}",
                    )
                }
            }
        }
    }

    companion object {
        private const val STATE_KEY_FORM = "profile_edit_form_v1"
        /** RFC 1035: a full DNS name can be up to 253 characters. */
        private const val MAX_DOMAIN_LENGTH = 253
        /** Soft cap so users can't accumulate runaway block lists that bloat the profile row. */
        private const val MAX_DOMAINS_PER_PROFILE = 250
    }
}

/**
 * Subset of [ProfileEditUiState] that we persist across process death. We
 * deliberately leave out transient flags (isSaving, isDeleting, savedSuccessfully,
 * deletedSuccessfully, errorMessage, isActiveSessionForThisProfile) and the
 * heavy [ProfileEditUiState.installedApps] list — those rehydrate naturally on
 * next launch.
 */
private data class PersistedFormState(
    val name: String,
    val strategyId: String,
    val timerMinutes: Int,
    val breakTimeInMinutes: Int,
    val enableStrictMode: Boolean,
    val enableLiveActivity: Boolean,
    val enableReminder: Boolean,
    val reminderTimeMinutes: Int,
    val customReminderMessage: String,
    val isAllowMode: Boolean,
    val isAllowModeDomains: Boolean,
    val blockAdultWebsites: Boolean,
    val domains: List<String>,
    val schedule: ProfileSchedule,
    val blockedPackages: List<String>,
) {
    fun applyTo(base: ProfileEditUiState): ProfileEditUiState {
        val migratedDraftAllowApps = isAllowMode
        val packages = if (migratedDraftAllowApps) emptySet() else blockedPackages.toSet()
        return base.copy(
            name = name,
            strategyId = strategyId,
            timerMinutes = timerMinutes,
            breakTimeInMinutes = breakTimeInMinutes,
            enableStrictMode = enableStrictMode,
            enableLiveActivity = enableLiveActivity,
            enableReminder = enableReminder,
            reminderTimeMinutes = reminderTimeMinutes,
            customReminderMessage = customReminderMessage,
            isAllowMode = false,
            isAllowModeDomains = isAllowModeDomains,
            blockAdultWebsites = blockAdultWebsites,
            domains = domains,
            schedule = schedule,
            blockedPackages = packages,
            errorMessage = if (migratedDraftAllowApps) {
                "Allow-only mode for apps is no longer available. Please select apps to restrict again."
            } else {
                base.errorMessage
            },
        )
    }

    companion object {
        fun from(state: ProfileEditUiState): PersistedFormState = PersistedFormState(
            name = state.name,
            strategyId = state.strategyId,
            timerMinutes = state.timerMinutes,
            breakTimeInMinutes = state.breakTimeInMinutes,
            enableStrictMode = state.enableStrictMode,
            enableLiveActivity = state.enableLiveActivity,
            enableReminder = state.enableReminder,
            reminderTimeMinutes = state.reminderTimeMinutes,
            customReminderMessage = state.customReminderMessage,
            isAllowMode = false,
            isAllowModeDomains = state.isAllowModeDomains,
            blockAdultWebsites = state.blockAdultWebsites,
            domains = state.domains,
            schedule = state.schedule,
            blockedPackages = state.blockedPackages.toList(),
        )
    }
}
