package com.qeetgroup.qeetid.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.BuildConfig
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.card.QeetrixCard
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionItem
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionList
import com.qeetgroup.qeetrix.components.dialog.QeetrixConfirmationDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogIntent
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.switchcontrol.QeetrixSwitch
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarHost
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarTone
import com.qeetgroup.qeetrix.components.toast.rememberQeetrixSnackbarHostState
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.format.QeetrixFormat
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch
import java.time.Instant

/** Help and privacy: Qeet ID's privacy notice. */
private const val HELP_PAGE = "https://id.qeet.in/legal/privacy"

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
    val scope = rememberCoroutineScope()
    val snackbars = rememberQeetrixSnackbarHostState()
    var confirmUnlink by remember { mutableStateOf(false) }
    val secure = remember { verification.isAvailable() }

    LaunchedEffect(model) {
        model.unlinkOutcomes.collect { outcome ->
            when (outcome) {
                UnlinkOutcome.Unlinked -> {
                    snackbars.show(resources.getString(R.string.unlinked), QeetrixSnackbarTone.Success)
                }

                UnlinkOutcome.AlreadyRemoved -> {
                    snackbars.show(
                        resources.getString(R.string.unlinked),
                        QeetrixSnackbarTone.Success,
                        resources.getString(R.string.unlinked_already),
                    )
                }

                UnlinkOutcome.FailedNetwork -> {
                    snackbars.show(
                        resources.getString(R.string.unlink_failed_title),
                        QeetrixSnackbarTone.Danger,
                        resources.getString(R.string.unlink_failed_network),
                    )
                }

                UnlinkOutcome.FailedRefused -> {
                    snackbars.show(
                        resources.getString(R.string.unlink_failed_title),
                        QeetrixSnackbarTone.Danger,
                        resources.getString(R.string.unlink_failed_refused),
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
        Screen(
            topBar = {
                QeetrixTopAppBar(
                    title = stringResource(R.string.settings_title),
                    navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
                )
            },
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(QeetrixSpace.InsetLg),
                verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
            ) {
                val current = link
                QeetrixCard(
                    Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_approvals),
                    actions = {
                        if (current != null) {
                            QeetrixBadge(stringResource(R.string.badge_on), tone = QeetrixBadgeTone.Success)
                        } else {
                            QeetrixBadge(stringResource(R.string.badge_off))
                        }
                    },
                    footer = {
                        if (current != null) {
                            QeetrixButton(
                                stringResource(R.string.settings_unlink),
                                onClick = { confirmUnlink = true },
                                variant = QeetrixButtonVariant.Ghost,
                                tone = QeetrixButtonTone.Danger,
                                size = QeetrixButtonSize.Sm,
                                loading = unlinking,
                            )
                        } else {
                            QeetrixButton(
                                stringResource(R.string.settings_link),
                                onClick = { navController.navigate(LinkPhone) },
                                variant = QeetrixButtonVariant.Ghost,
                                size = QeetrixButtonSize.Sm,
                            )
                        }
                    },
                ) {
                    if (current != null) {
                        QeetrixDescriptionList {
                            QeetrixDescriptionItem(
                                stringResource(R.string.settings_account),
                                current.accountEmail ?: stringResource(R.string.account_fallback),
                            )
                            QeetrixDescriptionItem(
                                stringResource(R.string.settings_this_phone),
                                stringResource(
                                    R.string.settings_this_phone_value,
                                    current.deviceName,
                                    QeetrixFormat.date(Instant.ofEpochMilli(current.linkedUntilMillis)),
                                ),
                            )
                        }
                    } else {
                        QeetrixText(stringResource(R.string.settings_not_linked_body), tone = QeetrixTextTone.Subtle)
                    }
                }

                QeetrixCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_security)) {
                    Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
                        QeetrixSwitch(
                            checked = appLock && secure,
                            onCheckedChange = ::changeLock,
                            label = stringResource(R.string.settings_app_lock),
                            description =
                                stringResource(
                                    if (secure) R.string.settings_app_lock_description else R.string.lock_needs_screen_lock,
                                ),
                            enabled = secure,
                        )
                        QeetrixSwitch(
                            checked = hideCodes,
                            onCheckedChange = model::setHideCodes,
                            label = stringResource(R.string.settings_hide_codes),
                            description = stringResource(R.string.settings_hide_codes_description),
                        )
                    }
                }

                QeetrixCard(Modifier.fillMaxWidth()) {
                    QeetrixListItem(
                        title = stringResource(R.string.settings_help),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, HELP_PAGE.toUri())) },
                        leading = { QeetrixListItemIcon(QeetrixIconName.Info) },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm),
                            ) {
                                QeetrixText(
                                    BuildConfig.VERSION_NAME,
                                    size = QeetrixTextSize.Sm,
                                    tone = QeetrixTextTone.Subtlest,
                                    mono = true,
                                )
                                QeetrixIcon(
                                    QeetrixIconName.ChevronRight,
                                    contentDescription = null,
                                    tint = QeetrixTheme.colors.iconSubtle,
                                    mirrorInRightToLeft = true,
                                )
                            }
                        },
                    )
                }
            }
        }
        QeetrixSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmUnlink) {
        val email = link?.accountEmail
        QeetrixConfirmationDialog(
            open = true,
            onOpenChange = { if (!it) confirmUnlink = false },
            title = stringResource(R.string.unlink_title),
            description = if (email != null) stringResource(R.string.unlink_body, email) else stringResource(R.string.unlink_body_generic),
            confirmLabel = stringResource(R.string.unlink_confirm),
            onConfirm = {
                confirmUnlink = false
                model.unlink()
            },
            intent = QeetrixDialogIntent.Danger,
        )
    }
}
