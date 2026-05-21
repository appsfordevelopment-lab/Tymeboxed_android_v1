package dev.ambitionsoftware.tymeboxed.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import dev.ambitionsoftware.tymeboxed.di.ThemeEntryPoint
import dev.ambitionsoftware.tymeboxed.domain.ShieldBlockMessage
import dev.ambitionsoftware.tymeboxed.domain.ShieldMessages

/**
 * Full-screen overlay shown when the user tries to open a blocked app.
 *
 * UI mirrors [awaseem/foqos](https://github.com/awaseem/foqos) Device Activity shields:
 * brand-color background, large emoji, title + subtitle, white pill button.
 *
 * Launched as a separate task (excluded from recents) that covers the blocked app.
 */
class BlockerActivity : ComponentActivity() {

    private val overlayIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this) { handleClose() }

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val themeController = EntryPointAccessors.fromApplication(
            applicationContext,
            ThemeEntryPoint::class.java,
        ).themeController()

        overlayIntent.value = intent

        setContent {
            val accent by themeController.primary.collectAsState(initial = BlockerAccent)
            val currentIntent = overlayIntent.value ?: intent
            val presentation = presentationFromIntent(currentIntent)
            BlockerTheme(background = accent) {
                BlockerScreen(
                    emoji = presentation.emoji,
                    headline = presentation.headline,
                    message = presentation.message,
                    buttonText = presentation.buttonText,
                    onDismiss = { handleClose() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        overlayIntent.value = intent
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        visibleBlockKey = intent?.getStringExtra(EXTRA_BLOCK_KEY)
    }

    override fun onPause() {
        isVisible = false
        visibleBlockKey = null
        super.onPause()
    }

    override fun onDestroy() {
        isVisible = false
        visibleBlockKey = null
        super.onDestroy()
    }

    private fun handleClose() {
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val postAckBackCount = intent.getIntExtra(EXTRA_POST_ACK_BACK_COUNT, 0)
        val returnToPackageOnClose = intent.getBooleanExtra(EXTRA_RETURN_TO_PACKAGE_ON_CLOSE, false)

        // Surface blocks: reveal the underlying app task instead of re-launching the package
        // (which can resume the blocked tab, e.g. Snapchat Map).
        if (returnToPackageOnClose && postAckBackCount <= 0) {
            finish()
            return
        }

        if (pkg.isNotBlank() && postAckBackCount > 0) {
            bringPackageToFront(this, pkg)
            queuePendingBackNavigation(pkg, postAckBackCount)
            finish()
            return
        }

        val dismissUrl = intent.getStringExtra(EXTRA_DISMISS_TO_URL)?.takeIf { it.isNotBlank() }
        val appHomePkg = intent.getStringExtra(EXTRA_DISMISS_APP_HOME_PKG)?.takeIf { it.isNotBlank() }
        when {
            dismissUrl != null -> openWebsite(
                context = this,
                url = dismissUrl,
                browserPkg = intent.getStringExtra(EXTRA_PKG),
            )
            intent.getBooleanExtra(EXTRA_DISMISS_TO_YT_HOME, false) -> openYouTubeHome(this)
            appHomePkg != null -> openAppHome(this, appHomePkg)
            else -> sendHome(this)
        }
        finish()
    }

    private data class BlockerPresentation(
        val emoji: String,
        val headline: String,
        val message: String,
        val buttonText: String,
    )

    private fun presentationFromIntent(intent: Intent): BlockerPresentation =
        BlockerPresentation(
            emoji = intent.getStringExtra(EXTRA_EMOJI)?.takeIf { it.isNotEmpty() } ?: "🛡️",
            headline = intent.getStringExtra(EXTRA_HEADLINE) ?: "Protected zone",
            message = intent.getStringExtra(EXTRA_BODY)
                ?: "We’re keeping your attention where you wanted it.",
            buttonText = intent.getStringExtra(EXTRA_BUTTON)?.takeIf { it.isNotEmpty() } ?: "Back",
        )

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_HEADLINE = "headline"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_EMOJI = "emoji"
        private const val EXTRA_BUTTON = "button"
        private const val EXTRA_BLOCK_KEY = "block_key"
        private const val EXTRA_DISMISS_TO_YT_HOME = "dismiss_to_yt_home"
        private const val EXTRA_DISMISS_APP_HOME_PKG = "dismiss_app_home_pkg"
        private const val EXTRA_DISMISS_TO_URL = "dismiss_to_url"
        private const val EXTRA_POST_ACK_BACK_COUNT = "post_ack_back_count"
        private const val EXTRA_RETURN_TO_PACKAGE_ON_CLOSE = "return_to_package_on_close"

        private const val PENDING_NAV_TTL_MS = 8_000L

        private data class PendingBackNavigation(
            val pkg: String,
            val backCount: Int,
            val createdAt: Long,
        )

        @Volatile
        private var pendingBackNavigation: PendingBackNavigation? = null

        /** Opened when the user dismisses a blocked-domain shield (Brick-style marketing redirect). */
        private const val BLOCKED_DOMAIN_DISMISS_URL = "https://tymeboxed.app/"

        @Volatile
        var isVisible: Boolean = false
            private set

        @Volatile
        var visibleBlockKey: String? = null
            private set

        private fun Intent.putShield(message: ShieldBlockMessage) {
            putExtra(EXTRA_EMOJI, message.emoji)
            putExtra(EXTRA_HEADLINE, message.title)
            putExtra(EXTRA_BODY, message.subtitle)
            putExtra(EXTRA_BUTTON, message.buttonText)
        }

        private fun Intent.putCustomShield(
            emoji: String,
            headline: String,
            body: String,
            buttonText: String = "Back",
        ) {
            putExtra(EXTRA_EMOJI, emoji)
            putExtra(EXTRA_HEADLINE, headline)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_BUTTON, buttonText)
        }

        fun show(
            context: Context,
            pkg: String,
            label: String?,
            headline: String? = null,
            body: String? = null,
            blockKey: String = pkg,
            dismissToYouTubeHome: Boolean = false,
            dismissToAppHomePkg: String? = null,
            postAcknowledgeBackCount: Int = 0,
            returnToPackageOnClose: Boolean = false,
        ) {
            val displayTitle = label?.takeIf { it.isNotBlank() } ?: pkg
            val i = Intent(context, BlockerActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_PKG, pkg)
                putExtra(EXTRA_BLOCK_KEY, blockKey)
                if (!label.isNullOrBlank()) putExtra(EXTRA_LABEL, label)
                when {
                    !headline.isNullOrBlank() || !body.isNullOrBlank() -> putCustomShield(
                        emoji = "🛡️",
                        headline = headline ?: "Protected zone",
                        body = body ?: "We’re keeping your attention where you wanted it.",
                    )
                    else -> putShield(ShieldMessages.forApp(displayTitle))
                }
                putExtra(EXTRA_DISMISS_TO_YT_HOME, dismissToYouTubeHome)
                if (!dismissToAppHomePkg.isNullOrBlank()) {
                    putExtra(EXTRA_DISMISS_APP_HOME_PKG, dismissToAppHomePkg)
                }
                if (postAcknowledgeBackCount > 0) {
                    putExtra(EXTRA_POST_ACK_BACK_COUNT, postAcknowledgeBackCount)
                }
                if (returnToPackageOnClose) {
                    putExtra(EXTRA_RETURN_TO_PACKAGE_ON_CLOSE, true)
                }
            }
            context.startActivity(i)
        }

        fun showForWebsite(context: Context, browserPkg: String, host: String) {
            val shield = ShieldMessages.forWebsite(host)
            val i = Intent(context, BlockerActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_PKG, browserPkg)
                putExtra(EXTRA_BLOCK_KEY, "$browserPkg|$host")
                putExtra(EXTRA_LABEL, host)
                putExtra(EXTRA_DISMISS_TO_URL, BLOCKED_DOMAIN_DISMISS_URL)
                putShield(shield)
            }
            context.startActivity(i)
        }

        fun showInApp(
            context: Context,
            pkg: String,
            appLabel: String,
            title: String,
            message: String,
            dismissToYouTubeHome: Boolean = false,
            dismissToAppHomePkg: String? = null,
            postAcknowledgeBackCount: Int = 0,
            returnToPackageOnClose: Boolean = false,
        ) {
            show(
                context = context,
                pkg = pkg,
                label = appLabel,
                blockKey = "$pkg|$title",
                headline = title,
                body = message,
                dismissToYouTubeHome = dismissToYouTubeHome,
                dismissToAppHomePkg = dismissToAppHomePkg,
                postAcknowledgeBackCount = postAcknowledgeBackCount,
                returnToPackageOnClose = returnToPackageOnClose,
            )
        }

        @Synchronized
        fun queuePendingBackNavigation(pkg: String, backCount: Int) {
            if (pkg.isBlank() || backCount <= 0) return
            pendingBackNavigation = PendingBackNavigation(
                pkg = pkg,
                backCount = backCount,
                createdAt = System.currentTimeMillis(),
            )
        }

        @Synchronized
        fun consumePendingBackNavigationFor(pkg: String): Int {
            val pending = pendingBackNavigation ?: return 0
            val now = System.currentTimeMillis()
            if ((now - pending.createdAt) > PENDING_NAV_TTL_MS) {
                pendingBackNavigation = null
                return 0
            }
            if (pending.pkg != pkg) return 0
            pendingBackNavigation = null
            return pending.backCount.coerceAtLeast(0)
        }

        private fun bringPackageToFront(context: Context, pkg: String) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                }?.let { context.startActivity(it) }
            }
        }

        fun openWebsite(context: Context, url: String, browserPkg: String?) {
            val view = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (!browserPkg.isNullOrBlank()) {
                view.setPackage(browserPkg)
            }
            if (runCatching { context.startActivity(view) }.isSuccess) return
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }.onFailure { sendHome(context) }
        }

        fun openYouTubeHome(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/".toUri()).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        fun openAppHome(context: Context, pkg: String) {
            val launch = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)
            }.getOrNull()
            if (launch != null) {
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
                if (runCatching { context.startActivity(launch) }.isSuccess) return
            }
            sendHome(context)
        }

        fun sendHome(context: Context) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
        }
    }
}

/** Default shield background — warm sandstone brand tone. */
private val BlockerAccent = Color(0xFF4B3E2F)

@Composable
private fun BlockerTheme(
    background: Color,
    content: @Composable () -> Unit,
) {
    val typography = Typography(
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = background,
            surface = background,
        ),
        typography = typography,
        content = content,
    )
}

@Composable
private fun BlockerScreen(
    emoji: String,
    headline: String,
    message: String,
    buttonText: String,
    onDismiss: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 76.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
