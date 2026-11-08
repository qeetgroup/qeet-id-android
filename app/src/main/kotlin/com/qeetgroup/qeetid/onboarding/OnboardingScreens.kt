package com.qeetgroup.qeetid.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.system.NotificationAccess
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.BrandMark
import com.qeetgroup.qeetid.ui.Fact
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.MarkRings
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.RingsSize
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.brandPanel
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetid.ui.isTablet
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetid.ui.windowHeightDp
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.progress.QeetrixProgress
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparator
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparatorTone
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixDensity
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.LocalQeetrixDensity
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

/** Welcoming pages use the comfortable density (design rules §17). */
@Composable
private fun Welcoming(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQeetrixDensity provides QeetrixDensity.Comfortable, content = content)
}

/**
 * Welcome: a soft brand band edge to edge under the status bar with the icon on brand rings, the promise, what the app
 * does in three ruled rows, and one way on.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val tablet = isTablet()
    val short = isShort()
    val tiny = !tablet && windowHeightDp() <= 640
    val device = deviceWord()
    val colors = QeetrixTheme.colors
    Welcoming {
        Screen(
            bottomBar = {
                QeetrixBottomActionBar {
                    Column(Modifier.readingColumn(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QeetrixButton(
                            stringResource(R.string.welcome_get_started),
                            onClick = onGetStarted,
                            size = QeetrixButtonSize.Lg,
                            icon = QeetrixIconName.ArrowRight,
                            iconMirrorsInRightToLeft = true,
                            fullWidth = true,
                        )
                        Spacer(Modifier.height(QeetrixSpace.GapSm))
                        QeetrixText(
                            stringResource(R.string.welcome_no_account),
                            size = QeetrixTextSize.Sm,
                            tone = QeetrixTextTone.Subtle,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The brand band: edge to edge, under the status bar.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(colors.bgBrandSubtle, colors.surfaceCanvas)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(
                            top = if (short) QeetrixSpace.GapSm else QeetrixSpace.LayoutSm,
                            bottom = if (short) QeetrixSpace.GapSm else QeetrixSpace.InsetLg,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MarkRings(
                        if (tablet) {
                            RingsSize.Lg
                        } else if (tiny) {
                            RingsSize.Xs
                        } else if (short) {
                            RingsSize.Sm
                        } else {
                            RingsSize.Md
                        },
                    )
                }
                Column(
                    Modifier
                        .readingColumn()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = QeetrixSpace.InsetXl),
                ) {
                    QeetrixHeading(
                        stringResource(R.string.welcome_title, device),
                        level = 1,
                        size = if (short) QeetrixHeadingSize.HeadingLg else QeetrixHeadingSize.DisplaySm,
                        modifier = Modifier.padding(top = QeetrixSpace.GapSm),
                    )
                    Spacer(Modifier.height(if (tiny) QeetrixSpace.GapSm else QeetrixSpace.GapMd))
                    QeetrixText(
                        stringResource(R.string.welcome_body),
                        size = if (tiny) QeetrixTextSize.Md else QeetrixTextSize.Lg,
                        tone = QeetrixTextTone.Subtle,
                    )
                    Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                    QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
                    WelcomeRow({
                        QeetrixIcon(QeetrixIconName.KeyRound, null, tint = colors.iconBrand)
                    }, R.string.welcome_codes_title, stringResource(R.string.welcome_codes_body), short)
                    QeetrixSeparator()
                    WelcomeRow({
                        QeetrixIcon(QeetrixIconName.ShieldCheck, null, tint = colors.iconBrand)
                    }, R.string.welcome_approvals_title, stringResource(R.string.welcome_approvals_body), short)
                    QeetrixSeparator()
                    WelcomeRow(
                        { QeetrixIcon(QeetrixIconName.Lock, null, size = QeetrixSize.IconMd, tint = colors.iconBrand) },
                        R.string.welcome_kept_title,
                        stringResource(R.string.welcome_kept_body, device),
                        short,
                        titleArg = device,
                    )
                }
            }
        }
    }
}

/** One thing the app does: an icon in the brand colour, a title and one line. */
@Composable
private fun WelcomeRow(
    icon: @Composable () -> Unit,
    title: Int,
    note: String,
    short: Boolean,
    titleArg: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (short) QeetrixSpace.InsetSm else QeetrixSpace.InsetMd),
        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
    ) {
        Box(Modifier.padding(top = 1.dp).clearAndSetSemantics {}) { icon() }
        Column {
            QeetrixText(
                if (titleArg !=
                    null
                ) {
                    stringResource(title, titleArg)
                } else {
                    stringResource(title)
                },
                weight = QeetrixTextWeight.Semibold,
            )
            QeetrixText(note, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }
}

/** Steps 1 and 2 of setting up: the step in the bar, and Qeetrix's progress under it. */
@Composable
private fun SetupBar(
    step: Int,
    totalSteps: Int,
    onBack: () -> Unit,
) {
    Column {
        QeetrixTopAppBar(
            title = null,
            description = stringResource(R.string.onboarding_step, step, totalSteps),
            navigationIcon = { QeetrixBackButton(onBack) },
        )
        QeetrixProgress(
            value = step * 100f / totalSteps,
            contentDescription = stringResource(R.string.onboarding_progress, step, totalSteps),
        )
    }
}

/**
 * Lock the app, step 1 of 2: a clear choice, Use fingerprint or Not now, what the lock does in three ticks, and that
 * approving always asks. A phone with no screen lock can't lock the app; the page says so and how to set one.
 */
@Composable
fun LockSetupScreen(
    totalSteps: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var secure by remember { mutableStateOf(verification.isAvailable()) }
    var busy by remember { mutableStateOf(false) }
    val tablet = isTablet()
    val short = isShort()
    val medium = !tablet && windowHeightDp() <= 800
    val device = deviceWord()
    LifecycleResumeEffect(Unit) {
        // Back from Settings with a screen lock set.
        secure = verification.isAvailable()
        onPauseOrDispose {}
    }
    val promptTitle = stringResource(R.string.lock_on_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_change_prompt_subtitle)

    Welcoming {
        ScrollingPage(
            topBar = { SetupBar(1, totalSteps, onBack) },
            actions = {
                if (secure) {
                    QeetrixButton(
                        stringResource(R.string.action_not_now),
                        onClick = {
                            container.preferences.setAppLock(false)
                            onDone()
                        },
                        variant = QeetrixButtonVariant.Ghost,
                        tone = QeetrixButtonTone.Neutral,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                    QeetrixButton(
                        stringResource(R.string.lock_use_fingerprint),
                        onClick = {
                            // Turning the lock on proves it can be passed, so nobody locks themselves out.
                            busy = true
                            scope.launch {
                                val result = verification.verify(VerifyPrompt(promptTitle, promptSubtitle))
                                busy = false
                                when (result) {
                                    VerifyResult.Verified -> {
                                        container.preferences.setAppLock(true)
                                        container.lock.unlock()
                                        onDone()
                                    }

                                    VerifyResult.Unavailable -> {
                                        container.preferences.setAppLock(false)
                                        secure = false
                                    }

                                    VerifyResult.Cancelled, VerifyResult.Failed -> {}
                                }
                            }
                        },
                        size = QeetrixButtonSize.Lg,
                        loading = busy,
                        fullWidth = true,
                    )
                } else {
                    QeetrixButton(
                        stringResource(R.string.action_open_settings),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        variant = QeetrixButtonVariant.Ghost,
                        tone = QeetrixButtonTone.Neutral,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                    QeetrixButton(
                        stringResource(R.string.lock_continue_without),
                        onClick = {
                            container.preferences.setAppLock(false)
                            onDone()
                        },
                        size = QeetrixButtonSize.Lg,
                        icon = QeetrixIconName.ArrowRight,
                        iconMirrorsInRightToLeft = true,
                        fullWidth = true,
                    )
                }
            },
        ) {
            if (!short) {
                MarkRings(
                    if (tablet) {
                        RingsSize.Md
                    } else if (medium) {
                        RingsSize.Xs
                    } else {
                        RingsSize.Sm
                    },
                    seal = { QeetrixIcon(QeetrixIconName.FingerprintPattern, null, Modifier.fillMaxSize()) },
                )
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            PageTitle(stringResource(R.string.lock_setup_title), stringResource(R.string.lock_setup_body, device))
            Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.LayoutSm))
            if (secure) {
                Panel {
                    Fact(stringResource(R.string.lock_fact_opens))
                    Fact(stringResource(R.string.lock_fact_fallback))
                    Fact(stringResource(R.string.lock_fact_hidden))
                }
                Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.InsetLg))
                QeetrixAlert(title = stringResource(R.string.lock_setup_approvals_note), tone = QeetrixAlertTone.Info)
            } else {
                QeetrixAlert(
                    title = stringResource(R.string.lock_needs_screen_lock_title),
                    description = stringResource(R.string.lock_needs_screen_lock, device) + " " + stringResource(R.string.lock_codes_still),
                    tone = QeetrixAlertTone.Warning,
                )
            }
        }
    }
}

/**
 * Get sign-in requests, step 2 of 2: the notification people will get, as the phone shows it, why, and what it
 * promises; then Android's own question. Refused for good, Allow opens the app's notification settings instead.
 */
@Composable
fun NotificationsPrimerScreen(
    totalSteps: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val short = isShort()
    val device = deviceWord()
    var refused by remember { mutableStateOf(NotificationAccess.refusedForGood(activity, container.preferences.notificationsAsked)) }
    LifecycleResumeEffect(Unit) {
        refused = NotificationAccess.refusedForGood(activity, container.preferences.notificationsAsked)
        if (NotificationAccess.enabled(context) && container.preferences.notificationsAsked) onDone()
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            container.preferences.notificationsAsked = true
            onDone()
        }

    Welcoming {
        ScrollingPage(
            topBar = { SetupBar(totalSteps, totalSteps, onBack) },
            actions = {
                QeetrixButton(
                    stringResource(R.string.action_not_now),
                    onClick = onDone,
                    variant = QeetrixButtonVariant.Ghost,
                    tone = QeetrixButtonTone.Neutral,
                    size = QeetrixButtonSize.Lg,
                    fullWidth = true,
                )
                QeetrixButton(
                    stringResource(if (refused) R.string.action_open_settings else R.string.notifications_allow),
                    onClick = {
                        when {
                            refused -> context.startActivity(NotificationAccess.settingsIntent(context))
                            NotificationAccess.permissionGranted(context) -> onDone()
                            else -> launcher.launch(NotificationAccess.PERMISSION)
                        }
                    },
                    size = QeetrixButtonSize.Lg,
                    icon = QeetrixIconName.Bell,
                    fullWidth = true,
                )
            },
        ) {
            // The request people will get, as the phone shows it.
            Box(Modifier.fillMaxWidth().brandPanel().padding(if (short) QeetrixSpace.InsetSm else QeetrixSpace.InsetMd)) {
                NotificationPreview()
            }
            Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.LayoutSm))
            PageTitle(stringResource(R.string.notifications_title, device), stringResource(R.string.notifications_body))
            Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.InsetLg))
            if (refused) {
                QeetrixAlert(title = stringResource(R.string.notifications_denied), tone = QeetrixAlertTone.Warning)
            } else {
                Panel {
                    Fact(stringResource(R.string.notifications_fact_only))
                    Fact(stringResource(R.string.notifications_fact_lock_screen))
                    if (!short) Fact(stringResource(R.string.notifications_fact_approve))
                }
            }
        }
    }
}

/**
 * A picture of the sign-in request as Android shows it: the app's icon and name, the time, the title and one line. A
 * picture of the phone's own card, so its rounded corners are the system's.
 */
@Composable
private fun NotificationPreview() {
    val colors = QeetrixTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(colors.surfaceOverlay, shape)
            .padding(QeetrixSpace.InsetMd)
            .clearAndSetSemantics {},
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 16.dp, contentDescription = null)
            QeetrixText(stringResource(R.string.notification_preview_app), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
        Spacer(Modifier.height(QeetrixSpace.GapSm))
        QeetrixText(stringResource(R.string.notification_title), weight = QeetrixTextWeight.Semibold)
        QeetrixText(stringResource(R.string.notification_body), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
    }
}
