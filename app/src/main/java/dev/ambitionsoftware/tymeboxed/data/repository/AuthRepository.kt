package dev.ambitionsoftware.tymeboxed.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.ambitionsoftware.tymeboxed.BuildConfig
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.nfc.nfcTagIdLookupCandidates
import dev.ambitionsoftware.tymeboxed.nfc.nfcUidHexCacheKey
import dev.ambitionsoftware.tymeboxed.nfc.NfcTagVerifyResult
import dev.ambitionsoftware.tymeboxed.util.networkDisplayMessageOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AuthOtpEnvelope(
    val success: Boolean? = null,
    val message: String? = null,
    val expiresIn: Int? = null,
)

private data class NfcVerifyEnvelope(
    val success: Boolean? = null,
    val valid: Boolean? = null,
    val message: String? = null,
)

/**
 * Email OTP against [BuildConfig.TYMEBOXED_API_BASE_URL] (production: https://api.tymeboxed.app).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val appPreferences: AppPreferences,
) {
    private val gson = Gson()
    private val baseUrl = BuildConfig.TYMEBOXED_API_BASE_URL.trimEnd('/')

    /**
     * Client-side per-tag cooldown so a hostile actor with our APK can't
     * enumerate the NFC tag namespace by hammering `/api/nfc/verify` from a
     * loop. The real defence is server-side rate-limiting + auth, but this
     * caps the rate any single client can sustain.
     */
    private val nfcLastAttemptAt = ConcurrentHashMap<String, Long>()

    companion object {
        private const val TAG = "AuthRepository"

        /** Minimum gap between two `/api/nfc/verify` calls for the same tag. */
        private const val NFC_VERIFY_MIN_INTERVAL_MS = 1_500L

        /** Generic header so the API can filter non-app traffic at the edge. */
        private const val APP_CLIENT_HEADER = "X-Tymeboxed-Client"
        private val APP_CLIENT_VALUE =
            "android/${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}"

        /**
         * Network timeouts (audit #31). [HttpURLConnection] defaults to 0 (infinite)
         * for both connect and read, which means a slow / unresponsive server pins
         * the calling coroutine and any UI awaiting it forever. Set conservative
         * upper bounds:
         *   - connect: 10 s — enough for retry over flaky cell networks but short
         *     enough that the UI can show "couldn't reach server" reasonably fast.
         *   - read: 15 s — the API endpoints we hit (auth + NFC verify) are
         *     sub-second under healthy load, so 15 s is generous.
         */
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    suspend fun sendOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        postAuth("/api/auth/send-otp", mapOf("email" to email))
    }

    suspend fun verifyOtp(email: String, otp: String): Result<Unit> = withContext(Dispatchers.IO) {
        postAuth("/api/auth/verify-otp", mapOf("email" to email, "otp" to otp))
    }

    /**
     * Whether [tagId] is an active Tyme Boxed device tag in the API database
     * (`POST /api/nfc/verify`). Does not require login.
     * Tries several [nfcTagIdLookupCandidates] (colon vs. continuous hex, case, etc.) so the string
     * matches how the record was stored in MongoDB.
     *
     * First successful server check for a given physical tag is stored in app preferences (DataStore);
     * later scans reuse that cache and skip the HTTP call (same behavior as iOS).
     */
    suspend fun verifyNfcTag(tagId: String): NfcTagVerifyResult = withContext(Dispatchers.IO) {
        val cacheKey = nfcUidHexCacheKey(tagId)
        if (cacheKey == null) {
            return@withContext NfcTagVerifyResult.Error("Empty tag id")
        }
        if (appPreferences.isNfcTagVerifiedInCache(cacheKey)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "NFC verify cache hit (redacted)")
            }
            return@withContext NfcTagVerifyResult.Registered
        }

        // Per-tag throttle. Prevents this client from being weaponised to
        // enumerate the tag namespace. We key off the cache key (a hash of
        // the UID) so log lines never contain the raw tag id.
        val now = System.currentTimeMillis()
        val lastAt = nfcLastAttemptAt[cacheKey] ?: 0L
        if (now - lastAt < NFC_VERIFY_MIN_INTERVAL_MS) {
            return@withContext NfcTagVerifyResult.Error("Slow down — try again in a moment.")
        }
        nfcLastAttemptAt[cacheKey] = now

        val candidates = nfcTagIdLookupCandidates(tagId)
        if (candidates.isEmpty()) {
            return@withContext NfcTagVerifyResult.Error("Empty tag id")
        }
        var sawHttpError: String? = null
        var sawValidFalse = false
        for (candidate in candidates) {
            val (code, text) = runCatching { postNfcVerifyHttp(candidate) }.getOrElse { e ->
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "NFC verify request failed: ${e.message}")
                }
                val msg = e.networkDisplayMessageOrNull()
                    ?: "Couldn't verify this tag. Check your connection and try again."
                return@withContext NfcTagVerifyResult.Error(msg)
            }
            if (code !in 200..299) {
                val msg = parseNfcErrorMessage(text) ?: text.ifBlank { "Request failed ($code)" }
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "NFC verify HTTP $code (body redacted)")
                }
                // Same route for all candidates; don’t keep failing the user per variant
                return@withContext NfcTagVerifyResult.Error("HTTP $code: $msg")
            }
            val valid = parseNfcValidFlag(text)
            if (valid == true) {
                appPreferences.putVerifiedNfcTagInCache(cacheKey)
                return@withContext NfcTagVerifyResult.Registered
            }
            if (valid == false) {
                sawValidFalse = true
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "NFC verify could not parse 'valid' (body redacted)")
                }
                sawHttpError = parseNfcErrorMessage(text) ?: "Unexpected response."
            }
        }
        when {
            sawValidFalse -> NfcTagVerifyResult.NotRegistered
            sawHttpError != null -> NfcTagVerifyResult.Error(sawHttpError)
            else -> NfcTagVerifyResult.Error("Could not verify this tag (no response from server).")
        }
    }

    private data class NfcHttpResult(val code: Int, val body: String)

    private fun postNfcVerifyHttp(tagId: String): NfcHttpResult {
        val jsonBody = gson.toJson(mapOf("tagId" to tagId))
        val url = URL(baseUrl + "/api/nfc/verify")
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty(APP_CLIENT_HEADER, APP_CLIENT_VALUE)
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            NfcHttpResult(code, text)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * `true` / `false` if the API clearly answered; [null] if JSON did not include a clear `valid` flag
     * (Gson and raw [JsonObject] for robustness).
     */
    private fun parseNfcValidFlag(responseBody: String): Boolean? {
        if (responseBody.isBlank()) return null
        val env = runCatching { gson.fromJson(responseBody, NfcVerifyEnvelope::class.java) }.getOrNull()
        when (val v = env?.valid) {
            true, false -> return v
            null -> Unit
        }
        val o = runCatching { JsonParser.parseString(responseBody).asJsonObject }.getOrNull() ?: return null
        val prim = when {
            o.has("valid") && !o.get("valid").isJsonNull -> o.get("valid")
            o.has("isValid") && !o.get("isValid").isJsonNull -> o.get("isValid")
            else -> return null
        }
        if (!prim.isJsonPrimitive) return null
        val p = prim.asJsonPrimitive
        return try {
            when {
                p.isBoolean -> p.asBoolean
                p.isString -> {
                    when (p.asString.trim().lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNfcErrorMessage(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        val o = runCatching { JsonParser.parseString(responseBody).asJsonObject }.getOrNull() ?: return null
        return o.get("message")?.asString
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Never log any part of the ID token (header, payload, audience).
        // It is bearer credential material; even in debug builds the JWT can
        // leak into Logcat exports, bug reports, or third-party crash SDKs.
        postAuth("/api/auth/google", mapOf("idToken" to idToken))
    }

    private fun postAuth(path: String, payload: Map<String, String>): Result<Unit> {
        val jsonBody = gson.toJson(payload)
        val url = URL(baseUrl + path)
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty(APP_CLIENT_HEADER, APP_CLIENT_VALUE)
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val env = runCatching { gson.fromJson(text, AuthOtpEnvelope::class.java) }.getOrNull()
            when {
                code in 200..299 && env?.success == true -> Result.success(Unit)
                env?.message != null -> Result.failure(Exception(env.message))
                text.isNotBlank() -> Result.failure(Exception(text))
                else -> Result.failure(Exception("Request failed ($code)"))
            }
        } catch (e: Exception) {
            val msg = e.networkDisplayMessageOrNull() ?: "Couldn't reach the server. Please try again."
            Result.failure(Exception(msg))
        } finally {
            conn?.disconnect()
        }
    }
}
