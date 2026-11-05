package com.qeetgroup.qeetid.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.codes.CloseButton
import com.qeetgroup.qeetid.scanner.QrScanner
import com.qeetgroup.qeetid.system.DeviceInfo
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.LetterTile
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.PhoneLinked
import com.qeetgroup.qeetid.ui.ScanPairing
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.card.QeetrixCard
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionItem
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionList
import com.qeetgroup.qeetrix.components.dialog.QeetrixAlertDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogAction
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogActionRole
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparator
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparatorTone
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.format.QeetrixFormat
import com.qeetgroup.qeetrix.foundation.haptics.QeetrixHapticRole
import com.qeetgroup.qeetrix.foundation.haptics.rememberQeetrixHaptics
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import java.time.Instant

/** The notes above the linking steps: already linked, or a build that cannot be linked. */
@Composable
private fun LinkNotices(
    linked: Boolean,
    availability: PushAvailability,
    onSettings: () -> Unit,
) {
    if (linked) {
        QeetrixAlert(
            title = stringResource(R.string.link_already_title),
            description = stringResource(R.string.link_already_body),
            tone = QeetrixAlertTone.Info,
            actions = {
                QeetrixButton(stringResource(R.string.link_go_settings), onClick = onSettings, size = QeetrixButtonSize.Sm)
            },
        )
    }
    when (availability) {
        PushAvailability.Unavailable ->
            QeetrixAlert(
                title = stringResource(R.string.link_unavailable_title),
                description = stringResource(R.string.link_unavailable_body),
                tone = QeetrixAlertTone.Warning,
            )
        PushAvailability.DebugStandIn ->
            QeetrixAlert(
                title = stringResource(R.string.link_stand_in_title),
                description = stringResource(R.string.link_stand_in_body),
                tone = QeetrixAlertTone.Info,
            )
        PushAvailability.Firebase -> Unit
    }
}

/** Screen 11: the console's own words for getting a pairing code, then scan; typing it is the quiet alternative. */
@Composable
fun LinkPhoneRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.links.get() }
    val canLink = link == null && container.pushTokens.availability != PushAvailability.Unavailable

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.link_title),
                navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
            )
        },
        bottomBar = {
            QeetrixBottomActionBar {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapXs)) {
                    QeetrixButton(
                        stringResource(R.string.link_scan),
                        onClick = { navController.navigate(ScanPairing) },
                        size = QeetrixButtonSize.Lg,
                        icon = QeetrixIconName.Camera,
                        fullWidth = true,
                        enabled = canLink,
                    )
                    QeetrixButton(
                        stringResource(R.string.link_type),
                        onClick = { navController.navigate(NamePhone()) },
                        variant = QeetrixButtonVariant.Ghost,
                        enabled = canLink,
                    )
                }
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(QeetrixSpace.InsetLg), verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
                LinkNotices(link != null, container.pushTokens.availability) { navController.navigate(Settings) }
                QeetrixText(stringResource(R.string.link_intro), tone = QeetrixTextTone.Subtle)
            }
            Column(Modifier.fillMaxWidth().background(QeetrixTheme.colors.surfaceRaised)) {
                QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
                Step("1", R.string.link_step1_title, R.string.link_step1_body)
                QeetrixSeparator()
                Step("2", R.string.link_step2_title, R.string.link_step2_body)
                QeetrixSeparator()
                Step("3", R.string.link_step3_title, R.string.link_step3_body)
                QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
            }
        }
    }
}

@Composable
private fun Step(
    number: String,
    title: Int,
    body: Int,
) {
    QeetrixListItem(
        title = stringResource(title),
        description = stringResource(body),
        leading = { LetterTile(number) },
    )
}

/** Screen 12: the same scanner, worded for linking; anything but a pairing code is refused with a reason. */
@Composable
fun ScanPairingRoute(navController: NavHostController) {
    var refused by remember { mutableStateOf(false) }
    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.link_title),
                navigationIcon = { CloseButton { navController.popBackStack() } },
            )
        },
    ) {
        QrScanner(
            guidance = stringResource(R.string.scan_pairing_guidance),
            onEnterKey = { navController.navigate(NamePhone()) { popUpTo(ScanPairing) { inclusive = true } } },
            paused = refused,
            modifier = Modifier.weight(1f),
            onCode = { text ->
                val code = PairingCode.from(text)
                if (code != null) {
                    navController.navigate(NamePhone(code)) { popUpTo(ScanPairing) { inclusive = true } }
                    true
                } else {
                    refused = true
                    false
                }
            },
        )
    }
    if (refused) {
        QeetrixAlertDialog(
            open = true,
            onOpenChange = { if (!it) refused = false },
            title = stringResource(R.string.scan_pairing_not_code_title),
            description = stringResource(R.string.scan_pairing_not_code_body),
            actions =
                listOf(
                    QeetrixDialogAction(stringResource(R.string.action_cancel), QeetrixDialogActionRole.Cancel) { navController.popBackStack() },
                    QeetrixDialogAction(stringResource(R.string.action_try_again)) { refused = false },
                ),
        )
    }
}

/**
 * Screen 13: the code, read-only when it was scanned or opened from a link (typed otherwise), and the phone's name,
 * prefilled. Linking happens only when the person taps Link phone.
 */
@Composable
fun NamePhoneRoute(
    navController: NavHostController,
    scannedCode: String?,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val model = viewModel { LinkingViewModel(container) }
    val state by model.state.collectAsStateWithLifecycle()
    val link by container.links.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.links.get() }
    val code = rememberTextFieldState(scannedCode.orEmpty())
    val name = rememberTextFieldState(DeviceInfo.name(context))
    val scroll = rememberScrollState()
    val haptics = rememberQeetrixHaptics()

    LaunchedEffect(state.linked) {
        if (state.linked) {
            haptics.perform(QeetrixHapticRole.Success)
            navController.navigate(PhoneLinked) { popUpTo(Accounts) }
        }
    }
    LaunchedEffect(state.problem) { if (state.problem != null) haptics.perform(QeetrixHapticRole.Error) }

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.link_title),
                navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
            )
        },
        bottomBar = {
            QeetrixBottomActionBar(scroll = scroll) {
                QeetrixButton(
                    stringResource(R.string.link_phone),
                    onClick = { model.link(code.text.toString(), name.text.toString()) },
                    size = QeetrixButtonSize.Lg,
                    icon = QeetrixIconName.ArrowRight,
                    iconMirrorsInRightToLeft = true,
                    loading = state.linking,
                    enabled = link == null,
                    fullWidth = true,
                )
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetXl),
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        ) {
            LinkNotices(link != null, model.availability) { navController.navigate(Settings) }
            state.problem?.let { problem ->
                if (problem != LinkProblem.InvalidCode) {
                    QeetrixAlert(
                        title = stringResource(R.string.link_error_title),
                        description = problemText(problem),
                        tone = QeetrixAlertTone.Danger,
                    )
                }
            }
            val codeError =
                when {
                    state.problem == LinkProblem.InvalidCode -> stringResource(R.string.link_error_invalid)
                    state.codeError -> stringResource(R.string.pairing_code_invalid)
                    else -> null
                }
            if (scannedCode != null) {
                QeetrixTextField(code, stringResource(R.string.pairing_code_label), readOnly = true, error = codeError)
            } else {
                QeetrixTextField(
                    code,
                    stringResource(R.string.pairing_code_label),
                    placeholder = stringResource(R.string.pairing_code_placeholder),
                    description = stringResource(R.string.pairing_code_help),
                    error = codeError,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                )
            }
            QeetrixTextField(
                name,
                stringResource(R.string.phone_name_label),
                description = stringResource(R.string.phone_name_help),
                error = if (state.nameError) stringResource(R.string.phone_name_required) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onKeyboardAction = { model.link(code.text.toString(), name.text.toString()) },
            )
            QeetrixCard(Modifier.fillMaxWidth()) {
                QeetrixDescriptionList {
                    QeetrixDescriptionItem(stringResource(R.string.link_requests_term), stringResource(R.string.link_requests_value))
                    QeetrixDescriptionItem(stringResource(R.string.link_stays_term), stringResource(R.string.link_stays_value))
                }
            }
        }
    }
}

@Composable
private fun problemText(problem: LinkProblem): String =
    when (problem) {
        LinkProblem.InvalidCode -> stringResource(R.string.link_error_invalid)
        is LinkProblem.Throttled -> pluralStringResource(R.plurals.link_error_throttled, problem.seconds, problem.seconds)
        LinkProblem.Network -> stringResource(R.string.link_error_network)
        LinkProblem.Refused -> stringResource(R.string.link_error_refused)
        LinkProblem.NoPushToken -> stringResource(R.string.link_error_no_push)
        LinkProblem.Unavailable -> stringResource(R.string.link_unavailable_body)
    }

/** Screen 14: the facts — which account, which phone, until when. */
@Composable
fun PhoneLinkedRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    val done = { navController.popBackStack(Accounts, inclusive = false) }
    Screen(
        topBar = {
            QeetrixTopAppBar(title = stringResource(R.string.link_title), navigationIcon = { CloseButton { done() } })
        },
        bottomBar = {
            QeetrixBottomActionBar {
                QeetrixButton(
                    stringResource(R.string.action_done),
                    onClick = { done() },
                    size = QeetrixButtonSize.Lg,
                    icon = QeetrixIconName.Check,
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
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        ) {
            QeetrixAlert(
                title = stringResource(R.string.linked_title),
                description = stringResource(R.string.linked_body),
                tone = QeetrixAlertTone.Success,
            )
            link?.let { summary ->
                QeetrixCard(Modifier.fillMaxWidth(), title = stringResource(R.string.linked_card)) {
                    QeetrixDescriptionList {
                        QeetrixDescriptionItem(stringResource(R.string.linked_account), summary.accountEmail ?: stringResource(R.string.account_fallback))
                        QeetrixDescriptionItem(stringResource(R.string.linked_name), summary.deviceName)
                        QeetrixDescriptionItem(
                            stringResource(R.string.linked_until),
                            stringResource(R.string.linked_until_value, QeetrixFormat.date(Instant.ofEpochMilli(summary.linkedUntilMillis))),
                        )
                    }
                }
            }
        }
    }
}
