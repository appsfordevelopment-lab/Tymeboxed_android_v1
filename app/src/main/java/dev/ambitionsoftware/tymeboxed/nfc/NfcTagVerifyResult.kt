package dev.ambitionsoftware.tymeboxed.nfc

/**
 * Result of [dev.ambitionsoftware.tymeboxed.data.repository.AuthRepository.verifyNfcTag]
 * against `POST /api/nfc/verify` (tag must exist in the server DB as an active Tyme Boxed device),
 * or [NfcTagVerifyResult.Registered] from local cache after a prior successful verify.
 */
sealed interface NfcTagVerifyResult {
    data object Registered : NfcTagVerifyResult
    data object NotRegistered : NfcTagVerifyResult
    data class Error(val message: String?) : NfcTagVerifyResult
}
