package dev.ambitionsoftware.tymeboxed.ui.screens.intro

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ambitionsoftware.tymeboxed.auth.GoogleSignInHelper
import dev.ambitionsoftware.tymeboxed.data.repository.AuthRepository
import dev.ambitionsoftware.tymeboxed.util.toDisplayMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class IntroAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _emailBusy = MutableStateFlow(false)
    val emailBusy: StateFlow<Boolean> = _emailBusy.asStateFlow()

    private val _googleBusy = MutableStateFlow(false)
    val googleBusy: StateFlow<Boolean> = _googleBusy.asStateFlow()

    private val _otpBusy = MutableStateFlow(false)
    val otpBusy: StateFlow<Boolean> = _otpBusy.asStateFlow()

    suspend fun requestOtp(email: String): Result<Unit> = authRepository.sendOtp(email.trim())

    suspend fun confirmOtp(email: String, code: String): Result<Unit> =
        authRepository.verifyOtp(email.trim(), code.trim())

    suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        authRepository.signInWithGoogle(idToken)

    /** Marks Google sign-in in flight until [handleGoogleSignInActivityResult] finishes. */
    fun markGoogleSignInStarted() {
        _googleBusy.value = true
    }

    /**
     * Processes the Google Sign-In activity result on [viewModelScope] so rotation does not
     * cancel the token exchange (unlike [rememberCoroutineScope] in the composable).
     */
    fun handleGoogleSignInActivityResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                GoogleSignInHelper.idTokenFromResult(data).fold(
                    onSuccess = { token ->
                        signInWithGoogle(token).fold(
                            onSuccess = { onSuccess() },
                            onFailure = { onError(it.toDisplayMessage("Could not sign in")) },
                        )
                    },
                    onFailure = { e ->
                        if (e !is GoogleSignInHelper.CancelledException) {
                            onError(e.toDisplayMessage("Google sign-in failed"))
                        }
                    },
                )
            } finally {
                _googleBusy.value = false
            }
        }
    }

    fun requestOtpForIntro(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _emailBusy.value = true
            requestOtp(email).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.toDisplayMessage("Something went wrong")) },
            )
            _emailBusy.value = false
        }
    }

    fun confirmOtpForIntro(
        email: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _otpBusy.value = true
            confirmOtp(email, code).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.toDisplayMessage("Something went wrong")) },
            )
            _otpBusy.value = false
        }
    }
}
