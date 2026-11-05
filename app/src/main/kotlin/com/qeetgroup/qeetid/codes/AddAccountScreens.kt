package com.qeetgroup.qeetid.codes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.PairingCode
import com.qeetgroup.qeetid.codes.totp.OtpAuthResult
import com.qeetgroup.qeetid.codes.totp.OtpAuthUri
import com.qeetgroup.qeetid.codes.totp.TotpAlgorithm
import com.qeetgroup.qeetid.scanner.QrScanner
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.ScanAccount
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.TypeKey
import com.qeetgroup.qeetrix.components.accordion.QeetrixAccordion
import com.qeetgroup.qeetrix.components.accordion.QeetrixAccordionItem
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixIconButton
import com.qeetgroup.qeetrix.components.dialog.QeetrixAlertDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogAction
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogActionRole
import com.qeetgroup.qeetrix.components.input.QeetrixOption
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.segmented.QeetrixSegmentedControl
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import kotlinx.coroutines.launch

/** The close (X) button of a screen opened over another. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    QeetrixIconButton(QeetrixIconName.X, stringResource(com.qeetgroup.qeetid.R.string.action_close), onClick = onClick, tone = QeetrixButtonTone.Neutral)
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

/** Screens 6 and 7: scan a setup QR code; a pairing link scanned here goes to linking. */
@Composable
fun ScanAccountRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<ScanProblem?>(null) }

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.scan_account_title),
                navigationIcon = { CloseButton { navController.popBackStack() } },
            )
        },
    ) {
        QrScanner(
            guidance = stringResource(R.string.scan_account_guidance),
            onEnterKey = { navController.navigate(TypeKey) { popUpTo(ScanAccount) { inclusive = true } } },
            paused = problem != null,
            modifier = Modifier.weight(1f),
            onCode = { text ->
                when (val result = OtpAuthUri.parse(text)) {
                    is OtpAuthResult.Valid -> {
                        scope.launch {
                            container.accounts.add(result.account)
                            navController.backToAccounts(result.account.issuer)
                        }
                        true
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
    }

    problem?.let { shown ->
        QeetrixAlertDialog(
            open = true,
            onOpenChange = { if (!it) problem = null },
            title = stringResource(shown.title),
            description = stringResource(shown.body),
            actions =
                listOf(
                    QeetrixDialogAction(stringResource(R.string.action_cancel), QeetrixDialogActionRole.Cancel) { navController.popBackStack() },
                    QeetrixDialogAction(stringResource(R.string.action_try_again)) { problem = null },
                ),
        )
    }
}

/** Screen 8: a setup key typed in, for when there is no QR code. */
@Composable
fun TypeKeyRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val service = rememberTextFieldState()
    val account = rememberTextFieldState()
    val key = rememberTextFieldState()
    var digits by rememberSaveable { mutableStateOf(6) }
    var period by rememberSaveable { mutableStateOf(30) }
    var algorithm by rememberSaveable { mutableStateOf(TotpAlgorithm.SHA1) }
    var advanced by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var serviceError by remember { mutableStateOf<Int?>(null) }
    var keyError by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun submit() {
        when (val result = TypeKeyForm.validate(service.text.toString(), account.text.toString(), key.text.toString(), digits, period, algorithm)) {
            is TypeKeyForm.Result.Invalid -> {
                serviceError = result.serviceError
                keyError = result.keyError
            }
            is TypeKeyForm.Result.Valid -> {
                serviceError = null
                keyError = null
                saving = true
                scope.launch {
                    container.accounts.add(result.account)
                    navController.backToAccounts(result.account.issuer)
                }
            }
        }
    }

    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.type_key_title),
                navigationIcon = { CloseButton { navController.popBackStack() } },
            )
        },
        bottomBar = {
            QeetrixBottomActionBar(scroll = scroll) {
                QeetrixButton(
                    stringResource(R.string.type_key_add),
                    onClick = ::submit,
                    size = QeetrixButtonSize.Lg,
                    icon = QeetrixIconName.Plus,
                    loading = saving,
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
                .padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetXl),
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        ) {
            QeetrixTextField(
                service,
                stringResource(R.string.type_key_service),
                placeholder = stringResource(R.string.type_key_service_placeholder),
                error = serviceError?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            QeetrixTextField(
                account,
                stringResource(R.string.type_key_account),
                placeholder = stringResource(R.string.type_key_account_placeholder),
                optional = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
            QeetrixTextField(
                key,
                stringResource(R.string.type_key_key),
                placeholder = stringResource(R.string.type_key_key_placeholder),
                description = stringResource(R.string.type_key_key_help),
                error = keyError?.let { stringResource(it) },
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
            QeetrixAccordion(value = advanced, onValueChange = { advanced = it }) {
                QeetrixAccordionItem(ADVANCED, stringResource(R.string.type_key_advanced)) {
                    Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
                        QeetrixText(
                            stringResource(R.string.type_key_summary, digits, period, algorithmLabel(algorithm)),
                            size = QeetrixTextSize.Sm,
                            tone = QeetrixTextTone.Subtle,
                        )
                        Labelled(stringResource(R.string.type_key_digits)) {
                            QeetrixSegmentedControl(digits, { digits = it }, listOf(QeetrixOption(6, "6"), QeetrixOption(8, "8")), Modifier.fillMaxWidth())
                        }
                        Labelled(stringResource(R.string.type_key_period)) {
                            QeetrixSegmentedControl(
                                period,
                                { period = it },
                                listOf(QeetrixOption(30, stringResource(R.string.period_30)), QeetrixOption(60, stringResource(R.string.period_60))),
                                Modifier.fillMaxWidth(),
                            )
                        }
                        Labelled(stringResource(R.string.type_key_algorithm)) {
                            QeetrixSegmentedControl(
                                algorithm,
                                { algorithm = it },
                                TotpAlgorithm.entries.map { QeetrixOption(it, algorithmLabel(it)) },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Labelled(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapXs)) {
        QeetrixText(label, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        content()
    }
}

private fun algorithmLabel(algorithm: TotpAlgorithm): String =
    when (algorithm) {
        TotpAlgorithm.SHA1 -> "SHA-1"
        TotpAlgorithm.SHA256 -> "SHA-256"
        TotpAlgorithm.SHA512 -> "SHA-512"
    }

private const val ADVANCED = "advanced"
