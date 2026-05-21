package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileSessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val profileId = MutableStateFlow<String?>(null)

    val sessions: StateFlow<List<Session>> = profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else sessionRepository.observeSessionsForProfile(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun startObserving(id: String) {
        profileId.value = id
    }

    fun stopObserving() {
        profileId.value = null
    }
}
