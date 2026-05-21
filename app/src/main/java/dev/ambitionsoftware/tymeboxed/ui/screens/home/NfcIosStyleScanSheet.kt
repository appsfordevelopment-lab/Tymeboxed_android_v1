@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ambitionsoftware.tymeboxed.ui.screens.home

import android.nfc.NfcAdapter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambitionsoftware.tymeboxed.MainActivity
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.nfc.NfcTagVerifyResult
import dev.ambitionsoftware.tymeboxed.nfc.normalizedUid
import kotlinx.coroutines.launch

private const val LOG_TAG = "NfcIosScan"

enum class NfcSessionScanPurpose {
    Start,
    Stop,
}

/**
 * System-style “Ready to Scan” sheet (iOS-like): [MaterialTheme] surface panel, ring + button
 * use [androidx.compose.material3.ColorScheme.primary] (the selected Settings accent).
 * After a read, [verifyNfc] is called; [onTagScanned] runs when the tag is registered. The first
 * successful `POST /api/nfc/verify` per UID is cached on device; later scans skip the network call.
 */
@Composable
fun NfcIosStyleScanSheet(
    profileName: String,
    purpose: NfcSessionScanPurpose,
    verifyNfc: suspend (String) -> NfcTagVerifyResult,
    onTagScanned: () -> Unit,
    onDismiss: () -> Unit,
    /** When set, overrides the default copy for the [purpose] (e.g. break vs end for focus+break). */
    bodyTextOverride: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val activity = LocalContext.current as ComponentActivity

    val latestOnSuccess by rememberUpdatedState(onTagScanned)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestVerifyNfc by rememberUpdatedState(verifyNfc)
    val scope = rememberCoroutineScope()
    // NFC reader callback is not recomposed; use a stable holder for flags the callback must read.
    val sessionFlags = remember {
        object {
            @Volatile
            var readerTornDown: Boolean = false
            @Volatile
            var verifying: Boolean = false
        }
    }
    var wrongTagMessage by remember { mutableStateOf<String?>(null) }
    val latestWrongTagMessage by rememberUpdatedState(wrongTagMessage)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden || latestWrongTagMessage == null
        },
    )

    val triggerName = profileName.ifBlank { "session" }
    val bodyText = bodyTextOverride ?: when (purpose) {
        NfcSessionScanPurpose.Start ->
            "Hold your phone near the Tyme Boxed device to start $triggerName."
        NfcSessionScanPurpose.Stop ->
            "Hold your phone near the Tyme Boxed device to end this session."
    }

    LaunchedEffect(Unit) {
        if (NfcAdapter.getDefaultAdapter(activity) == null) {
            Log.w(LOG_TAG, "No NFC adapter")
            latestDismiss()
        }
    }

    DisposableEffect(activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
            ?: return@DisposableEffect onDispose { }

        // Reuse the activity-wide flags so this sheet claims the tag identically
        // (incl. FLAG_READER_SKIP_NDEF_CHECK, which is what stops the OEM
        // "New tag collected — Empty tag" overlay from racing our callback).
        val flags = MainActivity.NFC_READER_FLAGS

        // Tell MainActivity we are taking over the reader-mode callback so its own onResume /
        // restore path doesn't fight us during the scan.
        (activity as? MainActivity)?.markNfcReaderHandedOff()

        adapter.enableReaderMode(
            activity,
            { tag ->
                val uid = tag.normalizedUid() ?: "unknown"
                Log.i(LOG_TAG, "Scanned uid=$uid")
                activity.runOnUiThread {
                    if (sessionFlags.readerTornDown || sessionFlags.verifying) return@runOnUiThread
                    if (uid == "unknown") {
                        wrongTagMessage = activity.getString(R.string.nfc_read_failed)
                        return@runOnUiThread
                    }
                    wrongTagMessage = null
                    sessionFlags.verifying = true
                }
                scope.launch {
                    try {
                        val result = latestVerifyNfc(uid)
                        activity.runOnUiThread {
                            sessionFlags.verifying = false
                            if (sessionFlags.readerTornDown) return@runOnUiThread
                            when (result) {
                                is NfcTagVerifyResult.Registered -> {
                                    // Mark torn down so further callback fires are ignored; the
                                    // actual reader-mode handoff back to MainActivity happens in
                                    // onDispose below to avoid a window where no callback is
                                    // installed at all (which is what caused the OEM "New tag
                                    // collected — Empty tag" popup / blank page on a follow-up tap).
                                    sessionFlags.readerTornDown = true
                                    // Prime MainActivity's dedup so the follow-up read of the
                                    // same physical tap is silently absorbed (no extra toast).
                                    (activity as? MainActivity)?.noteRecentlyConsumedNfcUid(uid)
                                    latestOnSuccess()
                                }
                                is NfcTagVerifyResult.NotRegistered -> {
                                    wrongTagMessage =
                                        activity.getString(R.string.nfc_use_tyme_boxed_device)
                                }
                                is NfcTagVerifyResult.Error -> {
                                    wrongTagMessage = result.message?.takeIf { it.isNotBlank() }
                                        ?: activity.getString(R.string.nfc_verify_network_error)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "NFC verify threw", e)
                        activity.runOnUiThread {
                            sessionFlags.verifying = false
                            if (sessionFlags.readerTornDown) return@runOnUiThread
                            wrongTagMessage =
                                activity.getString(R.string.nfc_verify_network_error)
                        }
                    }
                }
            },
            flags,
            null,
        )

        onDispose {
            sessionFlags.readerTornDown = true
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                // Atomically replace our callback with MainActivity's swallowing callback so a
                // still-nearby tag's follow-up read never escapes to the OEM NFC dispatcher.
                mainActivity.restoreSwallowingNfcReaderMode()
            } else {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    BackHandler {
        if (wrongTagMessage != null) {
            wrongTagMessage = null
        } else {
            onDismiss()
        }
    }

    val sheetTopRadius = 30.dp
    val pill = RoundedCornerShape(25.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(
            topStart = sheetTopRadius,
            topEnd = sheetTopRadius,
        ),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.2f)),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = "Ready to Scan",
                    color = colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 44.dp),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colorScheme.onSurface.copy(alpha = 0.1f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = bodyText,
                color = colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            IosNfcScanningHero(accent = colorScheme.primary)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    wrongTagMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { wrongTagMessage = null },
            title = {
                Text(text = stringResource(R.string.nfc_error_whoops_title))
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = { wrongTagMessage = null }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/**
 * iOS “Ready to Scan” style: soft outward ripples behind a **thick ring** in [accent] with
 * a same-tint **phone** icon in the open center (no filled disk).
 */
@Composable
private fun IosNfcScanningHero(
    accent: Color,
) {
    val transition = rememberInfiniteTransition(label = "nfcWave")
    val waveProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveProgress",
    )
    // Primary ring + phone scale together (system scanning pulse)
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val baseRingDp = 92.dp
    val wavePhases = listOf(0f, 1f / 3f, 2f / 3f)
    // Thick annulus + centered icon (iOS system sheet / reference screenshot)
    val mainRingSize = 152.dp
    val mainRingStroke = 9.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(210.dp),
    ) {
        wavePhases.forEach { offset ->
            val t = (waveProgress + offset) % 1f
            val scale = 0.45f + t * 1.1f
            val ringAlpha = ((1f - t).coerceIn(0f, 1f)) * 0.4f
            if (ringAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .size(baseRingDp)
                        .alpha(ringAlpha)
                        .scale(scale)
                        .border(2.dp, accent.copy(alpha = 0.65f), CircleShape),
                )
            }
        }
        // Thick hollow ring + same-color phone; pulses gently as one unit
        Box(
            modifier = Modifier
                .size(mainRingSize)
                .scale(breathe),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(mainRingStroke, accent, CircleShape),
            )
            Icon(
                imageVector = Icons.Filled.Smartphone,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center),
            )
        }
    }
}
