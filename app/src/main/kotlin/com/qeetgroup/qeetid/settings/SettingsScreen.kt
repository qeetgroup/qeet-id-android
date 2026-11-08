package com.qeetgroup.qeetid.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.BuildConfig
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.LinkSummary
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.system.NotificationAccess
import com.qeetgroup.qeetid.ui.About
import com.qeetgroup.qeetid.ui.AdminHome
import com.qeetgroup.qeetid.ui.AdminOrganizations
import com.qeetgroup.qeetid.ui.AdminSignIn
import com.qeetgroup.qeetid.ui.BrandMark
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.MoveDevice
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogIntent
import com.qeetgroup.qeetrix.components.switchcontrol.QeetrixSwitch
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarHost
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarTone
import com.qeetgroup.qeetrix.components.toast.rememberQeetrixSnackbarHostState
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.format.QeetrixFormat
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Settings: the phone's link to the account (by its email), whether requests can reach it, and Unlink; the app lock
 * and hidden codes; moving to a new phone; About and help. Same look as Accounts: titles in caps, full-width ruled rows
 * on a phone, 4 dp cards on a tablet.
 */
@Composable
fun SettingsRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val model = viewModel { SettingsViewModel(container) }
    val link by model.link.collectAsStateWithLifecycle()
    val appLock by model.appLock.collectAsStateWithLifecycle()
    val hideCodes by model.hideCodes.collectAsStateWithLifecycle()
    val unlinking by model.unlinking.collectAsStateWithLifecycle()
    val admin by container.admin.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.admin.load() }
    val scope = rememberCoroutineScope()
    val snackbars = rememberQeetrixSnackbarHostState()
    var confirmUnlink by remember { mutableStateOf(false) }
    var secure by remember { mutableStateOf(verification.isAvailable()) }
    var notificationsOn by remember { mutableStateOf(NotificationAccess.enabled(context)) }
    val device = deviceWord()

    // Back from the phone's Settings: notifications or the screen lock may have changed.
    LifecycleResumeEffect(Unit) {
        notificationsOn = NotificationAccess.enabled(context)
        secure = verification.isAvailable()
        onPauseOrDispose {}
    }

    LaunchedEffect(model) {
        model.unlinkOutcomes.collect { outcome ->
            when (outcome) {
                UnlinkOutcome.Unlinked -> {
                    snackbars.show(resources.getString(R.string.unlinked, device), QeetrixSnackbarTone.Success)
                }

                UnlinkOutcome.AlreadyRemoved -> {
                    snackbars.show(
                        resources.getString(R.string.unlinked, device),
                        QeetrixSnackbarTone.Success,
                        resources.getString(R.string.unlinked_already),
                    )
                }

                UnlinkOutcome.FailedNetwork -> {
                    snackbars.show(
                        resources.getString(R.string.unlink_failed_title, device),
                        QeetrixSnackbarTone.Danger,
                        resources.getString(R.string.unlink_failed_network, device),
                    )
                }

                UnlinkOutcome.FailedRefused -> {
                    snackbars.show(
                        resources.getString(R.string.unlink_failed_title, device),
                        QeetrixSnackbarTone.Danger,
                        resources.getString(R.string.unlink_failed_refused, device),
                    )
                }
            }
        }
    }

    fun changeLock(on: Boolean) {
        val title = resources.getString(if (on) R.string.lock_on_prompt_title else R.string.lock_off_prompt_title)
        val subtitle = resources.getString(R.string.lock_change_prompt_subtitle)
        scope.launch {
            when (verification.verify(VerifyPrompt(title, subtitle))) {
                VerifyResult.Verified -> model.setAppLock(on)
                VerifyResult.Unavailable -> if (!on) model.setAppLock(false)
                VerifyResult.Cancelled, VerifyResult.Failed -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Screen(topBar = { ScreenBar(stringResource(R.string.settings_title), onBack = { navController.popBackStack() }) }) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(top = 20.dp),
            ) {
                ApprovalsGroup(
                    link = link,
                    notificationsOn = notificationsOn,
                    unlinking = unlinking,
                    onOpenNotificationSettings = { context.startActivity(NotificationAccess.settingsIntent(context)) },
                    onUnlink = { confirmUnlink = true },
                    onLink = { navController.navigate(LinkPhone) },
                )

                SettingsGroup(stringResource(R.string.settings_security)) {
                    val lockLabel = stringResource(R.string.settings_app_lock)
                    SettingsRow(
                        label = lockLabel,
                        detail =
                            if (secure) {
                                stringResource(
                                    R.string.settings_app_lock_description,
                                )
                            } else {
                                stringResource(R.string.lock_needs_screen_lock, device)
                            },
                        end = {
                            QeetrixSwitch(
                                checked = appLock && secure,
                                onCheckedChange = ::changeLock,
                                modifier = Modifier.semantics { contentDescription = lockLabel },
                                enabled = secure,
                            )
                        },
                    )
                    SettingsRule()
                    val hideLabel = stringResource(R.string.settings_hide_codes)
                    SettingsRow(
                        label = hideLabel,
                        detail = stringResource(R.string.settings_hide_codes_description),
                        end = {
                            QeetrixSwitch(
                                checked = hideCodes,
                                onCheckedChange = model::setHideCodes,
                                modifier = Modifier.semantics { contentDescription = hideLabel },
                            )
                        },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_codes)) {
                    SettingsRow(label = stringResource(R.string.settings_move, device), onClick = { navController.navigate(MoveDevice) })
                }

                SettingsGroup(stringResource(R.string.settings_administration)) {
                    val signedIn = admin
                    SettingsRow(
                        label = stringResource(R.string.admin_title),
                        detail =
                            signedIn?.organizationName?.let { stringResource(R.string.settings_administration_in, it) }
                                ?: stringResource(R.string.settings_administration_body),
                        onClick = {
                            navController.navigate(
                                when {
                                    signedIn?.organizationId != null -> AdminHome
                                    signedIn != null -> AdminOrganizations
                                    else -> AdminSignIn
                                },
                            )
                        },
                    )
                }

                SettingsGroup(stringResource(R.string.settings_about)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_about_help),
                        value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        onClick = { navController.navigate(About) },
                    )
                }
            }
        }
        QeetrixSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmUnlink) {
        UnlinkDialog(
            email = link?.accountEmail,
            onDismiss = { confirmUnlink = false },
            onUnlink = {
                confirmUnlink = false
                model.unlink()
            },
        )
    }
}

/** Sign-in approvals: the account and this phone, whether requests reach it, and Unlink; or Link when it isn't linked. */
@Composable
private fun ApprovalsGroup(
    link: LinkSummary?,
    notificationsOn: Boolean,
    unlinking: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onUnlink: () -> Unit,
    onLink: () -> Unit,
) {
    val device = deviceWord()
    SettingsGroup(stringResource(R.string.settings_approvals)) {
        if (link != null) {
            SettingsRow(
                label = link.accountEmail ?: stringResource(R.string.account_fallback),
                detail =
                    stringResource(
                        R.string.settings_this_phone_value,
                        link.deviceName,
                        QeetrixFormat.date(Instant.ofEpochMilli(link.linkedUntilMillis)),
                    ),
                start = { BrandMark(size = 40.dp, contentDescription = null) },
                end = {
                    if (notificationsOn) {
                        QeetrixBadge(stringResource(R.string.badge_on), tone = QeetrixBadgeTone.Success)
                    } else {
                        QeetrixBadge(stringResource(R.string.badge_off), tone = QeetrixBadgeTone.Warning)
                    }
                },
            )
            SettingsRule()
            SettingsRow(
                label = stringResource(R.string.settings_notifications),
                detail = if (notificationsOn) null else stringResource(R.string.settings_notifications_off, device),
                end = {
                    if (notificationsOn) {
                        QeetrixText(
                            stringResource(R.string.settings_notifications_allowed),
                            size = QeetrixTextSize.Sm,
                            tone = QeetrixTextTone.Subtle,
                        )
                    } else {
                        QeetrixButton(
                            stringResource(R.string.action_open_settings),
                            onClick = onOpenNotificationSettings,
                            variant = QeetrixButtonVariant.Soft,
                            size = QeetrixButtonSize.Sm,
                        )
                    }
                },
            )
            SettingsRule()
            SettingsRow(
                label = stringResource(R.string.settings_unlink, device),
                danger = true,
                onClick = onUnlink.takeUnless { unlinking },
            )
        } else {
            SettingsRow(
                label = stringResource(R.string.settings_not_linked),
                detail = stringResource(R.string.settings_not_linked_body),
                start = { BrandMark(size = 40.dp, contentDescription = null) },
                end = { QeetrixButton(stringResource(R.string.settings_link), onClick = onLink, size = QeetrixButtonSize.Sm) },
            )
        }
    }
}

/**
 * Unlink this phone: Qeetrix's danger dialog. What stops (sign-in requests, at once) and what stays (the codes), and to
 * have another way to confirm sign-ins first.
 */
@Composable
private fun UnlinkDialog(
    email: String?,
    onDismiss: () -> Unit,
    onUnlink: () -> Unit,
) {
    val colors = QeetrixTheme.colors
    val device = deviceWord()
    QeetrixDialog(
        open = true,
        onOpenChange = { if (!it) onDismiss() },
        title = stringResource(R.string.unlink_title, device),
        description = email?.let { stringResource(R.string.unlink_from, it) },
        intent = QeetrixDialogIntent.Danger,
        footer = {
            QeetrixButton(
                stringResource(R.string.action_cancel),
                onClick = onDismiss,
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
            )
            QeetrixButton(stringResource(R.string.unlink_confirm), onClick = onUnlink, tone = QeetrixButtonTone.Danger)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
                QeetrixIcon(QeetrixIconName.X, null, Modifier.padding(top = 2.dp), size = QeetrixSize.IconSm, tint = colors.iconDanger)
                QeetrixText(stringResource(R.string.unlink_stops), size = QeetrixTextSize.Sm)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
                QeetrixIcon(QeetrixIconName.Check, null, Modifier.padding(top = 2.dp), size = QeetrixSize.IconSm, tint = colors.iconSuccess)
                QeetrixText(stringResource(R.string.unlink_stays, device), size = QeetrixTextSize.Sm)
            }
            QeetrixText(stringResource(R.string.unlink_ready), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }
}
