package com.qeetgroup.qeetid.onboarding

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.system.NotificationAccess
import com.qeetgroup.qeetid.ui.AppMark
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.card.QeetrixCard
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.permission.QeetrixPermission
import com.qeetgroup.qeetrix.components.permission.QeetrixPermissionPrimer
import com.qeetgroup.qeetrix.components.permission.QeetrixPermissionStatus
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparator
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparatorTone
import com.qeetgroup.qeetrix.components.switchcontrol.QeetrixSwitch
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixDensity
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.LocalQeetrixDensity
import kotlinx.coroutines.launch

/** Welcoming pages use the comfortable density (design rules §17). */
@Composable
private fun Welcoming(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQeetrixDensity provides QeetrixDensity.Comfortable, content = content)
}

/** Screen 1: what the app is for in one line, its two jobs as rows, one primary action. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Welcoming {
        Screen(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            bottomBar = {
                QeetrixBottomActionBar {
                    QeetrixButton(
                        stringResource(R.string.welcome_get_started),
                        onClick = onGetStarted,
                        size = QeetrixButtonSize.Lg,
                        icon = QeetrixIconName.ArrowRight,
                        iconMirrorsInRightToLeft = true,
                        fullWidth = true,
                    )
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = QeetrixSpace.InsetLg, end = QeetrixSpace.InsetLg, top = QeetrixSpace.LayoutSm)) {
                    AppMark()
                    Spacer(Modifier.height(QeetrixSpace.LayoutSm))
                    QeetrixHeading(stringResource(R.string.welcome_title), level = 1, size = QeetrixHeadingSize.DisplaySm)
                    Spacer(Modifier.height(QeetrixSpace.GapMd))
                    QeetrixText(stringResource(R.string.welcome_body), size = QeetrixTextSize.Lg, tone = QeetrixTextTone.Subtle)
                }
                Spacer(Modifier.height(QeetrixSpace.LayoutSm))
                QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
                FeatureRow(QeetrixIconName.Clock, R.string.welcome_codes_title, R.string.welcome_codes_body)
                QeetrixSeparator()
                FeatureRow(QeetrixIconName.CircleCheck, R.string.welcome_approvals_title, R.string.welcome_approvals_body)
                QeetrixSeparator()
                FeatureRow(QeetrixIconName.Lock, R.string.welcome_kept_title, R.string.welcome_kept_body)
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: QeetrixIconName,
    title: Int,
    body: Int,
) {
    QeetrixListItem(
        title = stringResource(title),
        description = stringResource(body),
        leading = { QeetrixListItemIcon(icon) },
    )
}

/** Screen 2: one switch, explained, and the note that approving always asks for the fingerprint or PIN. */
@Composable
fun LockSetupScreen(
    totalSteps: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val scope = rememberCoroutineScope()
    val secure = remember { verification.isAvailable() }
    var lockOn by remember { mutableStateOf(secure) }
    var busy by remember { mutableStateOf(false) }

    Welcoming {
        Screen(
            topBar = {
                QeetrixTopAppBar(
                    title = stringResource(R.string.onboarding_step, 1, totalSteps),
                    navigationIcon = { QeetrixBackButton(onBack) },
                )
            },
            bottomBar = {
                QeetrixBottomActionBar {
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
                    val title = stringResource(R.string.lock_on_prompt_title)
                    val subtitle = stringResource(R.string.lock_change_prompt_subtitle)
                    QeetrixButton(
                        stringResource(R.string.action_continue),
                        onClick = {
                            if (!lockOn) {
                                container.preferences.setAppLock(false)
                                onDone()
                                return@QeetrixButton
                            }
                            // Turning the lock on proves it can be passed, so nobody locks themselves out.
                            busy = true
                            scope.launch {
                                val result = verification.verify(VerifyPrompt(title, subtitle))
                                busy = false
                                when (result) {
                                    VerifyResult.Verified -> {
                                        container.preferences.setAppLock(true)
                                        container.lock.unlock()
                                        onDone()
                                    }

                                    VerifyResult.Unavailable -> {
                                        container.preferences.setAppLock(false)
                                        onDone()
                                    }

                                    VerifyResult.Cancelled, VerifyResult.Failed -> {}
                                }
                            }
                        },
                        size = QeetrixButtonSize.Lg,
                        icon = QeetrixIconName.ArrowRight,
                        iconMirrorsInRightToLeft = true,
                        loading = busy,
                        fullWidth = true,
                    )
                }
            },
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetXl),
                verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapLg),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
                    QeetrixHeading(stringResource(R.string.lock_setup_title), level = 1, size = QeetrixHeadingSize.HeadingMd)
                    QeetrixText(stringResource(R.string.lock_setup_body), size = QeetrixTextSize.Lg, tone = QeetrixTextTone.Subtle)
                }
                QeetrixCard(Modifier.fillMaxWidth()) {
                    QeetrixSwitch(
                        checked = lockOn && secure,
                        onCheckedChange = { lockOn = it },
                        label = stringResource(R.string.lock_switch_label),
                        description = stringResource(if (secure) R.string.lock_switch_description else R.string.lock_needs_screen_lock),
                        enabled = secure,
                    )
                }
                QeetrixAlert(title = stringResource(R.string.lock_setup_approvals_note), tone = QeetrixAlertTone.Info)
            }
        }
    }
}

/** Screen 3: the permission primer, so Android's own prompt comes with a reason. */
@Composable
fun NotificationsPrimerScreen(
    totalSteps: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current
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
        Screen(
            topBar = {
                QeetrixTopAppBar(
                    title = stringResource(R.string.onboarding_step, totalSteps, totalSteps),
                    navigationIcon = { QeetrixBackButton(onBack) },
                )
            },
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QeetrixPermissionPrimer(
                        QeetrixPermission.Notifications,
                        title = stringResource(R.string.notifications_title),
                        description = stringResource(R.string.notifications_body),
                        onContinue = {
                            if (NotificationAccess.permissionGranted(context)) {
                                onDone()
                            } else {
                                launcher.launch(NotificationAccess.PERMISSION)
                            }
                        },
                        onNotNow = onDone,
                        status = if (refused) QeetrixPermissionStatus.Denied else QeetrixPermissionStatus.NotAsked,
                        deniedDescription = stringResource(R.string.notifications_denied),
                        onOpenSettings = { context.startActivity(NotificationAccess.settingsIntent(context)) },
                    )
                    QeetrixText(
                        stringResource(R.string.notifications_footnote),
                        modifier = Modifier.padding(horizontal = QeetrixSpace.LayoutSm),
                        size = QeetrixTextSize.Sm,
                        tone = QeetrixTextTone.Subtle,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
