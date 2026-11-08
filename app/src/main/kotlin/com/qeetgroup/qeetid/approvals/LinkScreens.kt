package com.qeetgroup.qeetid.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.codes.totp.OtpAuthResult
import com.qeetgroup.qeetid.codes.totp.OtpAuthUri
import com.qeetgroup.qeetid.scanner.QrScanner
import com.qeetgroup.qeetid.scanner.ScanPanel
import com.qeetgroup.qeetid.system.DeviceInfo
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.BrandMark
import com.qeetgroup.qeetid.ui.Fact
import com.qeetgroup.qeetid.ui.LetterTile
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.NumberedSteps
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.PhoneLinked
import com.qeetgroup.qeetid.ui.Picture
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.ScanAccount
import com.qeetgroup.qeetid.ui.ScanPairing
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetid.ui.brandPanel
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionItem
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionList
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.qrcode.QeetrixQRCode
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparator
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparatorTone
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.format.QeetrixFormat
import com.qeetgroup.qeetrix.foundation.haptics.QeetrixHapticRole
import com.qeetgroup.qeetrix.foundation.haptics.rememberQeetrixHaptics
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
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
        PushAvailability.Unavailable -> {
            QeetrixAlert(
                title = stringResource(R.string.link_unavailable_title),
                description = stringResource(R.string.link_unavailable_body),
                tone = QeetrixAlertTone.Warning,
            )
        }

        PushAvailability.DebugStandIn -> {
            QeetrixAlert(
                title = stringResource(R.string.link_stand_in_title),
                description = stringResource(R.string.link_stand_in_body),
                tone = QeetrixAlertTone.Info,
            )
        }

        PushAvailability.Firebase -> {}
    }
}

/**
 * Link this phone: the idea at a glance (a computer showing Qeet ID's code, this phone with Qeet ID), what linking
 * gives, and the three steps on the computer in the console's own words; Scan the code, or Type the code.
 */
@Composable
fun LinkPhoneRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.links.get() }
    val canLink = link == null && container.pushTokens.availability != PushAvailability.Unavailable
    val short = isShort()
    val device = deviceWord()

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.link_title, device), onBack = { navController.popBackStack() }) },
        actions = {
            QeetrixButton(
                stringResource(R.string.link_type),
                onClick = { navController.navigate(NamePhone()) },
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
                size = QeetrixButtonSize.Lg,
                enabled = canLink,
                fullWidth = true,
            )
            QeetrixButton(
                stringResource(R.string.link_scan),
                onClick = { navController.navigate(ScanPairing) },
                size = QeetrixButtonSize.Lg,
                enabled = canLink,
                fullWidth = true,
            )
        },
    ) {
        LinkNotices(link != null, container.pushTokens.availability) { navController.navigate(Settings) }
        if (link != null || container.pushTokens.availability != PushAvailability.Firebase) Spacer(Modifier.height(QeetrixSpace.InsetLg))
        LinkScene(short)
        Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.LayoutSm))
        PageTitle(stringResource(R.string.link_heading), stringResource(R.string.link_intro, device))
        Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else QeetrixSpace.InsetLg))
        Panel {
            QeetrixText(
                stringResource(R.string.link_steps),
                size = QeetrixTextSize.Sm,
                weight = QeetrixTextWeight.Semibold,
                tone = QeetrixTextTone.Subtle,
            )
            NumberedSteps(
                listOf(
                    stringResource(R.string.link_step1_title) to stringResource(R.string.link_step1_body),
                    stringResource(R.string.link_step2_title) to stringResource(R.string.link_step2_body),
                    stringResource(R.string.link_step3_title) to stringResource(R.string.link_step3_body),
                ),
            )
        }
    }
}

/**
 * The idea at a glance, on the soft brand panel: a computer showing Qeet ID's pairing code (Qeetrix's QR code, dark on
 * white) and this phone with Qeet ID. The outlines follow the theme.
 */
@Composable
private fun LinkScene(short: Boolean) {
    val colors = QeetrixTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .brandPanel()
            .padding(vertical = if (short) QeetrixSpace.InsetMd else QeetrixSpace.InsetXl)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(if (short) QeetrixSpace.GapMd else QeetrixSpace.InsetLg, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(width = if (short) 100.dp else 128.dp, height = if (short) 66.dp else 84.dp)
                    .border(if (short) 4.dp else 5.dp, colors.textDefault, QeetrixShapes.Sm)
                    .background(Picture.Page, QeetrixShapes.Sm),
                contentAlignment = Alignment.Center,
            ) {
                QeetrixQRCode(SCENE_LINK, label = "", size = if (short) 46.dp else 58.dp)
            }
            Box(Modifier.size(width = if (short) 118.dp else 150.dp, height = 6.dp).background(colors.textSubtle, QeetrixShapes.Sm))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).background(colors.borderBrand))
            Box(Modifier.size(width = if (short) 16.dp else 24.dp, height = 2.dp).background(colors.borderBrand))
        }
        Box(
            Modifier
                .size(width = if (short) 43.dp else 54.dp, height = if (short) 76.dp else 96.dp)
                .border(3.dp, colors.textDefault, QeetrixShapes.Surface)
                .background(colors.surfaceRaised, QeetrixShapes.Surface),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(size = if (short) 22.dp else 28.dp, contentDescription = null)
        }
    }
}

/** The pairing link the picture's QR code holds: a made-up code. */
private const val SCENE_LINK = "qeetidauth://pair?code=7KQ2M9XH4T"

/**
 * Scan the code: the same scanner, worded for linking. A read code goes on to Name this phone by itself; a site's
 * sign-in QR code scanned here is named, with a way to add it to Accounts instead; a phone already linked isn't linked
 * twice.
 */
@Composable
fun ScanPairingRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.links.get() }
    var refused by remember { mutableStateOf<String?>(null) }
    val device = deviceWord()
    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.scan_pairing_title),
                navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
            )
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            QrScanner(
                guidance = stringResource(R.string.scan_pairing_guidance),
                onEnterKey = { navController.navigate(NamePhone()) { popUpTo(ScanPairing) { inclusive = true } } },
                paused = refused != null || link != null,
                modifier = Modifier.fillMaxSize(),
                onCode = { text ->
                    val code = PairingCode.from(text)
                    if (code != null) {
                        navController.navigate(NamePhone(code)) { popUpTo(ScanPairing) { inclusive = true } }
                        true
                    } else {
                        refused = text
                        false
                    }
                },
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .readingColumn()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(QeetrixSpace.InsetLg),
            ) {
                if (link != null) {
                    ScanPanel(
                        title = stringResource(R.string.scan_pairing_linked, device),
                        body =
                            link?.accountEmail?.let { stringResource(R.string.scan_pairing_linked_for, it) }
                                ?: stringResource(R.string.scan_pairing_linked_body),
                        icon = QeetrixIconName.Info,
                    ) {
                        QeetrixButton(stringResource(R.string.action_done), onClick = {
                            navController.popBackStack(Accounts, inclusive = false)
                        }, fullWidth = true, modifier = Modifier.weight(1f))
                    }
                }
                refused?.let { text ->
                    val signIn = OtpAuthUri.parse(text) is OtpAuthResult.Valid
                    ScanPanel(
                        title =
                            stringResource(
                                if (signIn) R.string.scan_pairing_sign_in_code_title else R.string.scan_pairing_not_code_title,
                            ),
                        body =
                            stringResource(
                                if (signIn) R.string.scan_pairing_sign_in_code_body else R.string.scan_pairing_not_code_body,
                                device,
                            ),
                        icon = QeetrixIconName.CircleAlert,
                        warning = true,
                    ) {
                        if (signIn) {
                            QeetrixButton(
                                stringResource(R.string.scan_pairing_add_instead),
                                onClick = { navController.navigate(ScanAccount) { popUpTo(ScanPairing) { inclusive = true } } },
                                variant = QeetrixButtonVariant.Soft,
                                tone = QeetrixButtonTone.Neutral,
                                fullWidth = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        QeetrixButton(
                            stringResource(R.string.scan_again),
                            onClick = { refused = null },
                            fullWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Name this phone: a name for it, as the computer will list it under Linked phones (the phone's model, filled in), and
 * what linking does. A typed code is asked for here. Linking happens only when the person taps Link. A used or expired
 * code shows here, since the server says so only when the code is used.
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
    val haptics = rememberQeetrixHaptics()
    val device = deviceWord()
    val codeFailed = state.problem == LinkProblem.InvalidCode

    LaunchedEffect(state.linked) {
        if (state.linked) {
            haptics.perform(QeetrixHapticRole.Success)
            navController.navigate(PhoneLinked) { popUpTo(Accounts) }
        }
    }
    LaunchedEffect(state.problem) { if (state.problem != null) haptics.perform(QeetrixHapticRole.Error) }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.link_title, device), onBack = { navController.popBackStack() }) },
        actions = {
            if (codeFailed && scannedCode != null) {
                QeetrixButton(
                    stringResource(R.string.link_scan_new),
                    onClick = { navController.navigate(ScanPairing) { popUpTo(Accounts) } },
                    size = QeetrixButtonSize.Lg,
                    fullWidth = true,
                )
            } else {
                QeetrixButton(
                    stringResource(R.string.link_phone, device),
                    onClick = { model.link(code.text.toString(), name.text.toString()) },
                    size = QeetrixButtonSize.Lg,
                    loading = state.linking,
                    enabled = link == null && name.text.isNotBlank(),
                    fullWidth = true,
                )
            }
        },
    ) {
        LinkNotices(link != null, model.availability) { navController.navigate(Settings) }
        if (codeFailed) {
            QeetrixAlert(
                title = stringResource(R.string.link_error_code_title),
                description = stringResource(R.string.link_error_invalid),
                tone = QeetrixAlertTone.Danger,
            )
            Spacer(Modifier.height(QeetrixSpace.InsetLg))
        }
        state.problem?.let { problem ->
            if (problem != LinkProblem.InvalidCode) {
                QeetrixAlert(
                    title = stringResource(R.string.link_error_title),
                    description = problemText(problem),
                    tone = QeetrixAlertTone.Danger,
                )
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
        }
        PageTitle(stringResource(R.string.name_phone_title, device), stringResource(R.string.name_phone_body))
        Spacer(Modifier.height(QeetrixSpace.LayoutSm))
        Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapLg)) {
            if (scannedCode == null) {
                QeetrixTextField(
                    code,
                    stringResource(R.string.pairing_code_label),
                    placeholder = stringResource(R.string.pairing_code_placeholder),
                    description = stringResource(R.string.pairing_code_help),
                    error = if (state.codeError) stringResource(R.string.pairing_code_invalid) else null,
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
                error = if (state.nameError || name.text.isBlank()) stringResource(R.string.phone_name_required) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onKeyboardAction = { model.link(code.text.toString(), name.text.toString()) },
            )
            if (!codeFailed) {
                Panel {
                    QeetrixText(
                        stringResource(R.string.name_phone_once),
                        size = QeetrixTextSize.Sm,
                        weight = QeetrixTextWeight.Semibold,
                        tone = QeetrixTextTone.Subtle,
                    )
                    Fact(stringResource(R.string.name_phone_fact_approve))
                    Fact(stringResource(R.string.name_phone_fact_check))
                    Fact(stringResource(R.string.name_phone_fact_undo))
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

/**
 * Phone linked: the icon with a green seal on the brand wash, the account it now approves sign-ins for and the name
 * the computer lists, and what happens next.
 */
@Composable
fun PhoneLinkedRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    val device = deviceWord()
    val done = { navController.popBackStack(Accounts, inclusive = false) }
    ResultPage(
        title = stringResource(R.string.linked_title, device),
        body = stringResource(R.string.linked_body),
        sealTone = SealTone.Success,
        seal = { QeetrixIcon(QeetrixIconName.Check, null, Modifier.fillMaxSize()) },
        actions = {
            QeetrixButton(stringResource(R.string.action_done), onClick = { done() }, size = QeetrixButtonSize.Lg, fullWidth = true)
        },
    ) {
        link?.let { summary ->
            Panel {
                QeetrixDescriptionList {
                    QeetrixDescriptionItem(
                        stringResource(R.string.linked_account),
                        summary.accountEmail ?: stringResource(R.string.account_fallback),
                    )
                    QeetrixDescriptionItem(stringResource(R.string.linked_name), summary.deviceName)
                }
            }
            Spacer(Modifier.height(QeetrixSpace.InsetLg))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
            AppIcon(
                AppIconName.BellRing,
                null,
                Modifier.padding(top = 2.dp),
                size = QeetrixSize.IconSm,
                tint = QeetrixTheme.colors.iconBrand,
            )
            QeetrixText(stringResource(R.string.linked_next, device), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }
}
