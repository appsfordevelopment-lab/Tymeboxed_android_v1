package dev.ambitionsoftware.tymeboxed.ui.screens.intro

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.ambitionsoftware.tymeboxed.BuildConfig
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.auth.GoogleSignInHelper
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.permissions.grantPermission
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsViewModel
import dev.ambitionsoftware.tymeboxed.permissions.TymePermission
import dev.ambitionsoftware.tymeboxed.ui.components.AccessibilityDisclosureDialog
import dev.ambitionsoftware.tymeboxed.ui.components.ActionButton
import dev.ambitionsoftware.tymeboxed.ui.components.PermissionRow
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardDivider
import dev.ambitionsoftware.tymeboxed.util.toDisplayMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.res.Configuration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles

private const val OtpLength = 6

/** iOS-style full-bleed connect / permissions explainer: true black/white, muted body, inverted CTA. */
private data class OnboardingConnectPalette(
    val pageBackground: Color,
    val title: Color,
    val body: Color,
    val link: Color,
    val backButtonBackground: Color,
    val backButtonContent: Color,
    val ctaBackground: Color,
    val ctaContent: Color,
    val iconTint: Color,
    val sectionTitle: Color,
    val cardSectionLabel: Color,
    val successGreen: Color,
)

@Composable
private fun rememberOnboardingConnectPalette(): OnboardingConnectPalette {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) {
            OnboardingConnectPalette(
                pageBackground = Color.Black,
                title = Color.White,
                body = Color(0xFFA1A1A1),
                link = Color(0xFF007AFF),
                backButtonBackground = Color.White.copy(alpha = 0.12f),
                backButtonContent = Color.White,
                ctaBackground = Color.White,
                ctaContent = Color.Black,
                iconTint = Color.White,
                sectionTitle = Color.White,
                cardSectionLabel = Color(0xFF8E8E93),
                successGreen = Color(0xFF32D74B),
            )
        } else {
            OnboardingConnectPalette(
                pageBackground = Color(0xFFFFFFFF),
                title = Color.Black,
                body = Color(0xFF3C3C43),
                link = Color(0xFF007AFF),
                backButtonBackground = Color(0xFFE5E5EA),
                backButtonContent = Color.Black,
                ctaBackground = Color.Black,
                ctaContent = Color.White,
                iconTint = Color.Black,
                sectionTitle = Color.Black,
                cardSectionLabel = Color(0xFF636366),
                successGreen = Color(0xFF2E7D32),
            )
        }
    }
}

@Composable
private fun OnboardingFullBleedSystemBarsEffect() {
    val view = LocalView.current
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val act = context as? ComponentActivity
        if (act != null) {
            val controller = WindowCompat.getInsetsController(act.window, view)
            val lightStatusNavIcons = !dark
            controller.isAppearanceLightStatusBars = lightStatusNavIcons
            controller.isAppearanceLightNavigationBars = lightStatusNavIcons
        }
        onDispose {
            val act2 = context as? ComponentActivity ?: return@onDispose
            val isNight = (act2.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(act2.window, view).apply {
                isAppearanceLightStatusBars = !isNight
                isAppearanceLightNavigationBars = !isNight
            }
        }
    }
}

/**
 * Onboarding: welcome → email → OTP verify → blocking explainer → permissions.
 */
@Composable
fun IntroScreen(
    onIntroComplete: () -> Unit,
    prefs: AppPreferences,
    initialStep: Int = 0,
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    /** `true` when the user signed in with Google (skipped OTP); drives back from the explainer. */
    var postAuthViaGoogle by rememberSaveable { mutableStateOf(false) }
    var signInEmail by rememberSaveable { mutableStateOf("") }
    val vm: PermissionsViewModel = hiltViewModel()
    val authVm: IntroAuthViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAfterReturningFromSettings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (step) {
        0 -> WelcomeStep(onContinue = { step = 1 })
        1 -> LoginStep(
            authVm = authVm,
            onBack = { step = 0 },
            onLoginSuccess = { email ->
                signInEmail = email.trim()
                step = 2
            },
            onGoogleSignInSuccess = {
                postAuthViaGoogle = true
                step = 3
            },
        )
        2 -> OtpStep(
            email = signInEmail,
            authVm = authVm,
            onBack = { step = 1 },
            onVerified = {
                postAuthViaGoogle = false
                step = 3
            },
        )
        3 -> ConnectExplainerStep(
            onContinue = { step = 4 },
            onBack = { step = if (postAuthViaGoogle) 1 else 2 },
        )
        4 -> PermissionsStep(
            vm = vm,
            onDone = {
                scope.launch {
                    prefs.setIntroCompleted(true)
                    onIntroComplete()
                }
            },
            onBack = { step = 3 },
        )
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }
    val enterProgress by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "welcomeFade",
    )

    val titleStyle = MaterialTheme.typography.displaySmall.copy(
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.4f),
            offset = Offset(0f, 1f),
            blurRadius = 2f,
        ),
    )
    val slideUp = (1f - enterProgress) * 24f

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.welcome_intro_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(enterProgress)
                    .offset(y = slideUp.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Reclaim your time",
                    style = titleStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Reclaim your mind",
                    style = titleStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(50.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://www.tymeboxed.app/".toUri()),
                        )
                    },
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.85f),
                    ),
                ) {
                    Text(
                        text = "I don't have a TymeBoxed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            IntroLegalFooter(
                isDark = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(enterProgress),
            )
        }
    }
}

@Composable
private fun IntroLegalFooter(
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val bodyColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF3C3C43)
    val linkColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
    val andColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF3C3C43).copy(alpha = 0.85f)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val annotated = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = bodyColor,
                fontSize = 12.sp,
            ),
        ) {
            append("By continuing, you agree to our ")
        }
        pushLink(LinkAnnotation.Url("https://www.tymeboxed.app/terms", styles = linkStyles))
        append("Terms")
        pop()
        withStyle(
            style = SpanStyle(
                color = andColor,
                fontSize = 12.sp,
            ),
        ) {
            append(" and ")
        }
        pushLink(LinkAnnotation.Url("https://www.tymeboxed.app/privacy", styles = linkStyles))
        append("Privacy Policy")
        pop()
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
        modifier = modifier,
    )
}

@Composable
private fun LoginStep(
    authVm: IntroAuthViewModel,
    onBack: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    onGoogleSignInSuccess: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    val emailBusy by authVm.emailBusy.collectAsState()
    val googleBusy by authVm.googleBusy.collectAsState()
    val busy = emailBusy || googleBusy
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as ComponentActivity
    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            errorText = null
            authVm.handleGoogleSignInActivityResult(
                data = res.data,
                onSuccess = onGoogleSignInSuccess,
                onError = { errorText = it },
            )
        }
    val emailOk = remember(email) { isValidEmail(email) }
    val fieldShape = RoundedCornerShape(16.dp)
    val cs = MaterialTheme.colorScheme
    val isDarkLogin = isSystemInDarkTheme()
    val fieldBg = cs.surface
    val muted = cs.onSurfaceVariant
    val backBtnBg = cs.surfaceContainerHigh
    val dividerLine = cs.outline.copy(alpha = if (isDarkLogin) 0.5f else 0.35f)
    val googleBg = cs.surface
    val googleBorder = cs.outline.copy(alpha = if (isDarkLogin) 0.5f else 0.4f)
    val primaryBtnContainer = if (isDarkLogin) fieldBg else cs.onSurface
    val primaryBtnContent = if (isDarkLogin) cs.onSurface else cs.surface
    val primaryBtnProgress = primaryBtnContent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = backBtnBg,
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Enter your email to get started",
                style = UiTextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    lineHeight = 34.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Email address",
                        color = muted,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = cs.onSurface,
                    unfocusedTextColor = cs.onSurface,
                    cursorColor = cs.primary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                ),
            )
            Text(
                text = "We'll send you a verification code to confirm it's you",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = muted,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            errorText?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }
            Button(
                onClick = {
                    if (!emailOk || busy) return@Button
                    errorText = null
                    authVm.requestOtpForIntro(
                        email = email,
                        onSuccess = { onLoginSuccess(email) },
                        onError = { errorText = it },
                    )
                },
                enabled = emailOk && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryBtnContainer,
                    contentColor = primaryBtnContent,
                    disabledContainerColor = fieldBg,
                    disabledContentColor = muted,
                ),
            ) {
                if (emailBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = primaryBtnProgress,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Get verification code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(dividerLine),
                )
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = muted,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(dividerLine),
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                    if (webClientId.isBlank()) {
                        errorText =
                            "Add your OAuth Web client ID to local.properties as GOOGLE_WEB_CLIENT_ID=… " +
                                "(Google Cloud Console, same project as this app’s SHA-1)."
                        return@Button
                    }
                    errorText = null
                    authVm.markGoogleSignInStarted()
                    val client = GoogleSignInHelper.client(activity, webClientId)
                    googleLauncher.launch(client.signInIntent)
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = googleBg,
                    contentColor = cs.onSurface,
                    disabledContainerColor = fieldBg,
                    disabledContentColor = muted,
                ),
                border = BorderStroke(1.dp, googleBorder),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (googleBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = cs.onSurface,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_google_sign_in),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OtpStep(
    email: String,
    authVm: IntroAuthViewModel,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    var otp by rememberSaveable { mutableStateOf("") }
    val busy by authVm.otpBusy.collectAsState()
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val fieldShape = RoundedCornerShape(16.dp)
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val fieldBg = cs.surface
    val muted = cs.onSurfaceVariant
    val backBtnBg = cs.surfaceContainerHigh
    val primaryBtnContainer = if (isDark) fieldBg else cs.onSurface
    val primaryBtnContent = if (isDark) cs.onSurface else cs.surface
    val otpComplete = otp.length == OtpLength

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = backBtnBg,
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Verify your email",
                style = UiTextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    lineHeight = 34.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(OtpLength) { index ->
                        val digit = otp.getOrNull(index)?.toString().orEmpty()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(fieldShape)
                                .background(fieldBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = digit,
                                style = UiTextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
                BasicTextField(
                    value = otp,
                    onValueChange = { raw ->
                        otp = raw.filter { it.isDigit() }.take(OtpLength)
                        errorText = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.Transparent,
                        fontSize = 1.sp,
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    singleLine = true,
                )
            }
            Text(
                text = "Enter the 6-digit code sent to $email",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = muted,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            errorText?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }
            Button(
                onClick = {
                    if (!otpComplete || busy) return@Button
                    errorText = null
                    authVm.confirmOtpForIntro(
                        email = email,
                        code = otp,
                        onSuccess = onVerified,
                        onError = { errorText = it },
                    )
                },
                enabled = otpComplete && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = fieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryBtnContainer,
                    contentColor = primaryBtnContent,
                    disabledContainerColor = fieldBg,
                    disabledContentColor = muted,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = primaryBtnContent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Verify",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectExplainerStep(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val p = rememberOnboardingConnectPalette()
    val scroll = rememberScrollState()
    OnboardingFullBleedSystemBarsEffect()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = p.backButtonBackground,
                    contentColor = p.backButtonContent,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Set up Tyme Boxed",
                color = p.title,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Grant a few permissions to get started",
                color = p.body,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))

            ConnectExplainerRow(
                palette = p,
                icon = Icons.Filled.Settings,
                title = "How you'll use this",
                body = "These permissions let Tyme Boxed block restricted apps during focus sessions, " +
                    "start and end them on time, and read your Tyme Boxed tag when you tap.",
            )
            Spacer(modifier = Modifier.height(44.dp))
            val privacyText = buildAnnotatedString {
                append(
                    "We never see which apps you block, your usage data, or your browsing history. ",
                )
                append("Your information stays private and stored only on your device. ")
                pushLink(
                    LinkAnnotation.Url(
                        "https://www.tymeboxed.app/privacy",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = p.link,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                )
                append("Learn more in our privacy policy.")
                pop()
            }
            ConnectExplainerRow(
                palette = p,
                icon = Icons.Filled.Lock,
                title = "How we'll use this",
                bodyAnnotated = {
                    Text(
                        text = privacyText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = p.body,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        ),
                    )
                },
            )
            Spacer(modifier = Modifier.height(44.dp))
            ConnectExplainerRow(
                palette = p,
                icon = Icons.Filled.Star,
                title = "Why it matters",
                body = "This is how Tyme Boxed helps you create focused, intentional time - " +
                    "without deleting apps.",
            )
            Spacer(modifier = Modifier.height(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionButton(
                title = "Allow access",
                onClick = onContinue,
                backgroundColor = p.ctaBackground,
                contentColor = p.ctaContent,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "On the next screen, tap Grant beside each permission. Android may open " +
                    "Settings - use the back gesture to return here.",
                style = MaterialTheme.typography.bodySmall,
                color = p.body,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectExplainerRow(
    palette: OnboardingConnectPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String? = null,
    bodyAnnotated: (@Composable () -> Unit)? = null,
) {
    val sectionTitle = MaterialTheme.typography.bodyLarge.copy(
        color = palette.sectionTitle,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = palette.body,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = palette.iconTint,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = sectionTitle,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (bodyAnnotated != null) {
                bodyAnnotated()
            } else {
                Text(
                    text = body.orEmpty(),
                    style = bodyStyle,
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    vm: PermissionsViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val states by vm.states.collectAsState()
    val p = rememberOnboardingConnectPalette()
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    OnboardingFullBleedSystemBarsEffect()
    // Device Admin (Admin access) is requested only when Strict mode is enabled in Settings.
    // Never show it during onboarding/sign-up.
    val required = TymePermission.requiredPermissions.filter { it != TymePermission.DEVICE_ADMIN }
    val grantedRequiredCount = remember(states) {
        required.count { states[it] == true }
    }
    val allRequiredGranted = remember(states) {
        required.all { states[it] == true }
    }
    val requiredTotal = required.size
    val descriptionText = when {
        grantedRequiredCount == 0 ->
            "Tyme Boxed needs these permissions to block apps and keep sessions running. Accessibility access detects restricted apps and browser URLs on-device only — nothing is collected or shared."
        grantedRequiredCount >= requiredTotal ->
            "You're all set. Change anytime in Settings."
        else ->
            "Almost set. Allow the rest so blocking works properly."
    }
    val statusText = "$grantedRequiredCount of $requiredTotal required granted"
    val statusColor = if (allRequiredGranted) p.successGreen else Color(0xFFE5A000)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = p.backButtonBackground,
                    contentColor = p.backButtonContent,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Grant access",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = p.title,
                lineHeight = 32.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = p.body,
                lineHeight = 22.sp,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.cardSectionLabel,
                    modifier = Modifier.padding(start = 4.dp),
                )
                SettingsCard(title = null) {
                    required.forEachIndexed { idx, perm ->
                        val nfcUnavailable = perm == TymePermission.NFC && !vm.isNfcAvailable
                        PermissionRow(
                            permission = perm,
                            granted = states[perm] == true,
                            onGrantClick = {
                                if (perm == TymePermission.ACCESSIBILITY) {
                                    showAccessibilityDisclosure = true
                                } else {
                                    grantPermission(ctx, perm)
                                }
                            },
                            unavailable = nfcUnavailable,
                        )
                        if (idx < required.lastIndex) {
                            SettingsCardDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionButton(
                title = "Continue",
                onClick = onDone,
                enabled = allRequiredGranted,
                backgroundColor = p.ctaBackground,
                contentColor = p.ctaContent,
            )
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDisclosure = false },
            onConfirmOpenSettings = {
                showAccessibilityDisclosure = false
                grantPermission(ctx, TymePermission.ACCESSIBILITY)
            },
        )
    }
}

private fun isValidEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val regex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return regex.matches(trimmed)
}
