package com.qeetgroup.qeetid.codes

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.PairingCode
import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.codes.totp.OtpAuthResult
import com.qeetgroup.qeetid.codes.totp.OtpAuthUri
import com.qeetgroup.qeetid.codes.totp.TotpAlgorithm
import com.qeetgroup.qeetid.scanner.QrScanner
import com.qeetgroup.qeetid.scanner.ScanPanel
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.LetterTile
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.ScanAccount
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.TypeKey
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.initialOf
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.accordion.QeetrixAccordion
import com.qeetgroup.qeetrix.components.accordion.QeetrixAccordionItem
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.button.QeetrixIconButton
import com.qeetgroup.qeetrix.components.input.QeetrixOption
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.segmented.QeetrixSegmentedControl
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

/** The close (X) button of a screen opened over another. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    QeetrixIconButton(
        QeetrixIconName.X,
        stringResource(com.qeetgroup.qeetid.R.string.action_close),
        onClick = onClick,
        tone = QeetrixButtonTone.Neutral,
    )
}

/** Back on the Accounts screen, saying [issuer] was added. */
private fun NavHostController.backToAccounts(issuer: String) {
    runCatching { getBackStackEntry(Accounts).savedStateHandle[ADDED_ACCOUNT_KEY] = issuer }
    popBackStack(Accounts, inclusive = false)
}

private enum class ScanProblem(
    val title: Int,
    val body: Int,
) {
    NotACode(R.string.scan_not_code_title, R.string.scan_not_code_body),
    CounterBased(R.string.scan_counter_title, R.string.scan_counter_body),
}

/**
 * A scanned account waiting for Add. Not snapshot state: its secret stays out of anything observable, logged or
 * saved; only the issuer and account are shown.
 */
private class PendingScan {
    var account: NewAccount? = null
}

/**
 * Scan a QR code: Qeetrix's scanner over the camera, then what was found, to confirm before it's added (a wrong or
 * unexpected code is caught); an account already on the phone isn't added twice; another kind of QR code is named. A
 * pairing link scanned here goes to linking.
 */
@Composable
fun ScanAccountRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val pending = remember { PendingScan() }
    var problem by remember { mutableStateOf<ScanProblem?>(null) }
    var found by remember { mutableStateOf<Pair<String, String>?>(null) }
    var already by remember { mutableStateOf<Pair<String, String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(added) { added?.let(navController::backToAccounts) }
    DisposableEffect(Unit) { onDispose { pending.account = null } }

    fun scanAgain() {
        pending.account = null
        found = null
        already = null
        problem = null
    }

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.scan_account_title),
                navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
            )
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            QrScanner(
                guidance = stringResource(R.string.scan_account_guidance),
                enterKeyLabel = stringResource(R.string.accounts_type_key),
                deniedDescription = stringResource(R.string.scan_camera_off_body),
                onEnterKey = { navController.navigate(TypeKey) { popUpTo(ScanAccount) { inclusive = true } } },
                paused = problem != null || found != null || already != null,
                modifier = Modifier.fillMaxSize(),
                onCode = { text ->
                    when (val result = OtpAuthUri.parse(text)) {
                        is OtpAuthResult.Valid -> {
                            val account = result.account
                            val existing =
                                container.accounts.accounts.value.orEmpty().any {
                                    it.issuer.equals(account.issuer.trim(), ignoreCase = true) &&
                                        it.label.equals(account.label.trim(), ignoreCase = true)
                                }
                            if (existing) {
                                already = account.issuer to account.label
                            } else {
                                pending.account = account
                                found = account.issuer to account.label
                            }
                            false
                        }

                        OtpAuthResult.CounterBased -> {
                            problem = ScanProblem.CounterBased
                            false
                        }

                        OtpAuthResult.Invalid -> {
                            val code = if (PairingCode.isPairingLink(text)) PairingCode.from(text) else null
                            if (code != null) {
                                navController.navigate(NamePhone(code)) { popUpTo(ScanAccount) { inclusive = true } }
                                true
                            } else {
                                problem = ScanProblem.NotACode
                                false
                            }
                        }
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
                found?.let { (issuer, label) ->
                    ScanPanel(
                        title = stringResource(R.string.scan_found),
                        body = listOf(issuer, label).filter { it.isNotBlank() }.joinToString(" · "),
                        leading = { QeetrixListItemIcon(initialOf(issuer)) },
                    ) {
                        QeetrixButton(
                            stringResource(R.string.scan_again),
                            onClick = ::scanAgain,
                            variant = QeetrixButtonVariant.Soft,
                            tone = QeetrixButtonTone.Neutral,
                            fullWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                        QeetrixButton(stringResource(R.string.accounts_add), onClick = {
                            val account = pending.account ?: return@QeetrixButton
                            adding = true
                            scope.launch {
                                added = container.accounts.add(account).issuer
                                pending.account = null
                            }
                        }, loading = adding, fullWidth = true, modifier = Modifier.weight(1f))
                    }
                }
                already?.let { (issuer, label) ->
                    ScanPanel(
                        title =
                            stringResource(
                                R.string.scan_already,
                                listOf(issuer, label).filter { it.isNotBlank() }.joinToString(" · "),
                                deviceWord(),
                            ),
                        body = stringResource(R.string.scan_already_body),
                        icon = QeetrixIconName.Info,
                    ) {
                        QeetrixButton(
                            stringResource(R.string.scan_show_it),
                            onClick = {
                                navController.popBackStack(Accounts, inclusive = false)
                            },
                            variant = QeetrixButtonVariant.Soft,
                            tone = QeetrixButtonTone.Neutral,
                            fullWidth = true,
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    ),
                        )
                        QeetrixButton(
                            stringResource(R.string.scan_again),
                            onClick = ::scanAgain,
                            fullWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                problem?.let { shown ->
                    ScanPanel(
                        title = stringResource(shown.title),
                        body = stringResource(shown.body),
                        icon = QeetrixIconName.CircleAlert,
                        warning = true,
                    ) {
                        QeetrixButton(
                            stringResource(R.string.scan_again),
                            onClick = ::scanAgain,
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
 * Type the setup key: where to find it, then the site, the account and the key (grouped in fours as it's typed, a
 * wrong character explained as soon as it's there), with the rare code settings folded away. Add account waits for a
 * name and a whole key.
 */
@Composable
fun TypeKeyRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val service = rememberTextFieldState()
    val account = rememberTextFieldState()
    val key = rememberTextFieldState()
    var digits by rememberSaveable { mutableIntStateOf(6) }
    var period by rememberSaveable { mutableIntStateOf(30) }
    var algorithm by rememberSaveable { mutableStateOf(TotpAlgorithm.SHA1) }
    var advanced by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var serviceError by remember { mutableStateOf<Int?>(null) }
    var keyError by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(added) { added?.let(navController::backToAccounts) }
    val scroll = rememberScrollState()
    val context = LocalContext.current
    // Checked as it's typed: only a wrong character is pointed out live; a short key waits for Add.
    val bareKey =
        key.text
            .toString()
            .filterNot { it.isWhitespace() || it == '-' }
            .trimEnd('=')
    val wrongCharacter = bareKey.any { it !in 'A'..'Z' && it !in '2'..'7' }
    val ready = service.text.isNotBlank() && bareKey.length >= KEY_CHARS && !wrongCharacter

    fun submit() {
        when (
            val result =
                TypeKeyForm.validate(
                    service.text.toString(),
                    account.text.toString(),
                    key.text.toString(),
                    digits,
                    period,
                    algorithm,
                )
        ) {
            is TypeKeyForm.Result.Invalid -> {
                serviceError = result.serviceError
                keyError = result.keyError
            }

            is TypeKeyForm.Result.Valid -> {
                serviceError = null
                keyError = null
                saving = true
                scope.launch { added = container.accounts.add(result.account).issuer }
            }
        }
    }

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.type_key_title),
                navigationIcon = { QeetrixBackButton({ navController.popBackStack() }) },
            )
        },
        bottomBar = {
            QeetrixBottomActionBar(scroll = scroll) {
                QeetrixButton(
                    stringResource(R.string.type_key_add),
                    onClick = ::submit,
                    size = QeetrixButtonSize.Lg,
                    loading = saving,
                    enabled = ready,
                    fullWidth = true,
                )
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .readingColumn()
                .padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetXl),
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapLg),
        ) {
            QeetrixText(stringResource(R.string.type_key_intro), tone = QeetrixTextTone.Subtle)
            QeetrixTextField(
                service,
                stringResource(R.string.type_key_service),
                placeholder = stringResource(R.string.type_key_service_placeholder),
                error = serviceError?.let { stringResource(it) },
                startAdornment = {
                    if (service.text.isBlank()) {
                        AppIcon(AppIconName.Globe, null, size = QeetrixSize.IconSm, tint = QeetrixTheme.colors.iconSubtle)
                    } else {
                        LetterTile(initialOf(service.text.toString()), size = 22.dp)
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            QeetrixTextField(
                account,
                stringResource(R.string.type_key_account),
                placeholder = stringResource(R.string.type_key_account_placeholder),
                description = stringResource(R.string.type_key_account_help),
                optional = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
            )
            QeetrixTextField(
                key,
                stringResource(R.string.type_key_key),
                mono = true,
                placeholder = stringResource(R.string.type_key_key_placeholder),
                description = stringResource(R.string.type_key_key_help),
                error = if (wrongCharacter) stringResource(R.string.type_key_key_invalid) else keyError?.let { stringResource(it) },
                inputTransformation = KeyGrouping,
                endAdornment = {
                    QeetrixButton(
                        stringResource(R.string.type_key_paste),
                        onClick = { clipboardText(context)?.let { pasted -> key.setTextAndPlaceCursorAtEnd(group(pasted)) } },
                        variant = QeetrixButtonVariant.Ghost,
                        size = QeetrixButtonSize.Sm,
                    )
                },
                // A password keyboard: no suggestions, and nothing the keyboard learns from.
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                onKeyboardAction = { submit() },
            )
            // Code settings stay closed: almost every site uses 6 digits, every 30 s and SHA-1. The closed row says
            // what's set; each setting is a row with its name and a small segmented choice at the end.
            QeetrixAccordion(value = advanced, onValueChange = { advanced = it }) {
                QeetrixAccordionItem(
                    ADVANCED,
                    stringResource(R.string.type_key_advanced),
                    description =
                        stringResource(
                            R.string.type_key_summary,
                            pluralStringResource(R.plurals.type_key_digits_count, digits, digits),
                            pluralStringResource(R.plurals.type_key_every_seconds, period, period),
                            algorithmLabel(algorithm),
                        ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        QeetrixText(
                            stringResource(R.string.type_key_settings_note),
                            size = QeetrixTextSize.Sm,
                            tone = QeetrixTextTone.Subtle,
                        )
                        Setting(stringResource(R.string.type_key_digits)) {
                            QeetrixSegmentedControl(
                                digits,
                                { digits = it },
                                listOf(QeetrixOption(6, "6"), QeetrixOption(8, "8")),
                                size = QeetrixButtonSize.Sm,
                            )
                        }
                        Setting(stringResource(R.string.type_key_period)) {
                            QeetrixSegmentedControl(
                                period,
                                { period = it },
                                listOf(30, 60).map { QeetrixOption(it, stringResource(R.string.type_key_seconds, it)) },
                                size = QeetrixButtonSize.Sm,
                            )
                        }
                        Setting(stringResource(R.string.type_key_algorithm)) {
                            QeetrixSegmentedControl(
                                algorithm,
                                { algorithm = it },
                                TotpAlgorithm.entries.map { QeetrixOption(it, algorithmLabel(it)) },
                                size = QeetrixButtonSize.Sm,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One code setting: its name at the start and its segmented choice at the end of the row, at its own width; under the
 * name when they don't fit side by side (large text).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setting(
    label: String,
    choice: @Composable () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        QeetrixText(label, modifier = Modifier.padding(end = QeetrixSpace.GapLg))
        choice()
    }
}

private fun algorithmLabel(algorithm: TotpAlgorithm): String =
    when (algorithm) {
        TotpAlgorithm.SHA1 -> "SHA-1"
        TotpAlgorithm.SHA256 -> "SHA-256"
        TotpAlgorithm.SHA512 -> "SHA-512"
    }

private const val ADVANCED = "advanced"

/** 16 characters, 80 bits: [TypeKeyForm.MIN_KEY_BYTES] of base32. */
private const val KEY_CHARS = 16

/** The key as the site shows it: capitals, in groups of four. */
private fun group(raw: String): String =
    raw
        .uppercase()
        .filterNot { it.isWhitespace() || it == '-' }
        .chunked(4)
        .joinToString(" ")

/** Groups the key in fours, in capitals, as it's typed or pasted. */
private object KeyGrouping : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val grouped = group(toString())
        if (grouped != toString()) replace(0, length, grouped)
    }
}

/** The clipboard's text, read only when the person taps Paste. */
private fun clipboardText(context: Context): String? =
    context
        .getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
