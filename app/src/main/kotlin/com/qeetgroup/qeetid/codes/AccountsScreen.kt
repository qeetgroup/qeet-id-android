package com.qeetgroup.qeetid.codes

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.system.NotificationAccess
import com.qeetgroup.qeetid.ui.LetterTile
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.ScanAccount
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetid.ui.SheetAction
import com.qeetgroup.qeetid.ui.TypeKey
import com.qeetgroup.qeetid.ui.initialOf
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertVariant
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.button.QeetrixIconButton
import com.qeetgroup.qeetrix.components.dialog.QeetrixConfirmationDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogIntent
import com.qeetgroup.qeetrix.components.drawer.QeetrixBottomSheet
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyStateSize
import com.qeetgroup.qeetrix.components.input.QeetrixSearchField
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon
import com.qeetgroup.qeetrix.components.list.QeetrixPullToRefresh
import com.qeetgroup.qeetrix.components.list.QeetrixSectionHeader
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBarSize
import com.qeetgroup.qeetrix.components.navigation.rememberQeetrixTopAppBarScrollState
import com.qeetgroup.qeetrix.components.otpinput.QeetrixOTPCode
import com.qeetgroup.qeetrix.components.otpinput.QeetrixOTPCodeSize
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparator
import com.qeetgroup.qeetrix.components.separator.QeetrixSeparatorTone
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarHost
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarTone
import com.qeetgroup.qeetrix.components.toast.rememberQeetrixSnackbarHostState
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** The key the add-account screens leave on the Accounts entry, so it can say what was added. */
const val ADDED_ACCOUNT_KEY = "added_account"

@Composable
fun AccountsRoute(
    navController: NavHostController,
    entry: NavBackStackEntry,
) {
    val container = LocalAppContainer.current
    val model = viewModel { AccountsViewModel(container) }
    val state by model.state.collectAsStateWithLifecycle()
    val added by entry.savedStateHandle.getStateFlow<String?>(ADDED_ACCOUNT_KEY, null).collectAsStateWithLifecycle()
    AccountsScreen(
        state = state,
        model = model,
        added = added,
        onAddedShown = { entry.savedStateHandle[ADDED_ACCOUNT_KEY] = null },
        onAdd = { navController.navigate(ScanAccount) },
        onTypeKey = { navController.navigate(TypeKey) },
        onLink = { navController.navigate(LinkPhone) },
        onSettings = { navController.navigate(Settings) },
    )
}

/** Screens 4, 5, 9, 10, 24, 25 and 26: the accounts, their codes, and what to do with them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    state: AccountsState,
    model: AccountsViewModel,
    added: String?,
    onAddedShown: () -> Unit,
    onAdd: () -> Unit,
    onTypeKey: () -> Unit,
    onLink: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snackbars = rememberQeetrixSnackbarHostState()
    val barScroll = rememberQeetrixTopAppBarScrollState()
    var actionsFor by remember { mutableStateOf<AccountRow?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountRow?>(null) }
    var renaming by remember { mutableStateOf<AccountRow?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(NotificationAccess.enabled(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = NotificationAccess.enabled(context)
        onPauseOrDispose {}
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            container.preferences.notificationsAsked = true
            notificationsOn = NotificationAccess.enabled(context)
        }

    val copiedTitle = stringResource(R.string.copied)
    val copiedNote = stringResource(R.string.copied_clears)
    val resources = LocalResources.current
    LaunchedEffect(model) {
        model.events.collect { message ->
            when (message) {
                AccountsMessage.Copied -> {
                    snackbars.show(copiedTitle, QeetrixSnackbarTone.Success, copiedNote)
                }

                is AccountsMessage.Deleted -> {
                    snackbars.show(
                        resources.getString(R.string.account_deleted, message.issuer),
                        QeetrixSnackbarTone.Success,
                    )
                }
            }
        }
    }
    LaunchedEffect(added) {
        val issuer = added ?: return@LaunchedEffect
        onAddedShown()
        snackbars.show(resources.getString(R.string.account_added, issuer), QeetrixSnackbarTone.Success)
    }

    Box(Modifier.fillMaxSize()) {
        Screen(
            topBar = {
                QeetrixTopAppBar(
                    title = stringResource(R.string.accounts_title),
                    description =
                        if (state.total == 0) {
                            stringResource(R.string.accounts_none)
                        } else {
                            pluralStringResource(R.plurals.accounts_count, state.total, state.total)
                        },
                    size = QeetrixTopAppBarSize.Large,
                    scrollState = barScroll,
                    actions = {
                        if (state.total > 0) {
                            QeetrixIconButton(
                                QeetrixIconName.Plus,
                                stringResource(R.string.accounts_add),
                                onClick = onAdd,
                                tone = QeetrixButtonTone.Neutral,
                            )
                        }
                        QeetrixIconButton(
                            QeetrixIconName.Settings,
                            stringResource(R.string.accounts_settings),
                            onClick = onSettings,
                            tone = QeetrixButtonTone.Neutral,
                        )
                    },
                )
            },
        ) {
            QeetrixPullToRefresh(
                refreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        model.refresh().join()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(barScroll.nestedScrollConnection)
                        .testTag("accounts"),
                ) {
                    val linked = state.link != null
                    if (state.vaultReset) {
                        item(key = "vault-reset") {
                            QeetrixAlert(
                                title = stringResource(R.string.alert_vault_reset_title),
                                description = stringResource(R.string.alert_vault_reset_body),
                                tone = QeetrixAlertTone.Warning,
                                variant = QeetrixAlertVariant.Banner,
                            )
                        }
                    }
                    if (linked && !notificationsOn) {
                        item(key = "notifications-off") {
                            QeetrixAlert(
                                title = stringResource(R.string.alert_notifications_off_title),
                                description = stringResource(R.string.alert_notifications_off_body),
                                tone = QeetrixAlertTone.Warning,
                                variant = QeetrixAlertVariant.Banner,
                                actions = {
                                    QeetrixButton(
                                        stringResource(R.string.alert_notifications_off_action),
                                        onClick = {
                                            val askable =
                                                NotificationAccess.needsPermission &&
                                                    !NotificationAccess.permissionGranted(context) &&
                                                    !NotificationAccess.refusedForGood(activity, container.preferences.notificationsAsked)
                                            if (askable) {
                                                permission.launch(NotificationAccess.PERMISSION)
                                            } else {
                                                context.startActivity(NotificationAccess.settingsIntent(context))
                                            }
                                        },
                                        size = QeetrixButtonSize.Sm,
                                    )
                                },
                            )
                        }
                    }
                    if (linked && !state.online) {
                        item(key = "offline") {
                            QeetrixAlert(
                                title = stringResource(R.string.alert_offline_title),
                                description = stringResource(R.string.alert_offline_body),
                                tone = QeetrixAlertTone.Danger,
                                variant = QeetrixAlertVariant.Banner,
                            )
                        }
                    }
                    if (!state.loaded) return@LazyColumn
                    if (state.total == 0) {
                        item(key = "empty") {
                            EmptyAccounts(linked = linked, onScan = onAdd, onTypeKey = onTypeKey, onLink = onLink)
                        }
                        return@LazyColumn
                    }
                    item(key = "search") {
                        val search = rememberTextFieldState(state.query)
                        Box(
                            Modifier
                                .background(
                                    QeetrixTheme.colors.surfaceRaised,
                                ).padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetMd),
                        ) {
                            QeetrixSearchField(
                                search,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = stringResource(R.string.accounts_search),
                                onSearch = model::search,
                            )
                        }
                    }
                    item(key = "approvals") {
                        Column(Modifier.background(QeetrixTheme.colors.surfaceRaised)) {
                            ApprovalsRow(state, onClick = if (linked) onSettings else onLink)
                            QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
                        }
                    }
                    item(key = "codes-header") { QeetrixSectionHeader(stringResource(R.string.accounts_codes)) }
                    if (state.rows.isEmpty()) {
                        item(key = "no-match") {
                            QeetrixEmptyState(
                                stringResource(R.string.accounts_no_match_title),
                                icon = QeetrixIconName.SearchX,
                                description = stringResource(R.string.accounts_no_match_body),
                                size = QeetrixEmptyStateSize.Section,
                            )
                        }
                    }
                    items(state.rows, key = { it.id }) { row ->
                        Column(Modifier.background(QeetrixTheme.colors.surfaceRaised)) {
                            AccountRowItem(
                                row = row,
                                hidden = state.hideCodes,
                                selected = sheetOpen && actionsFor?.id == row.id,
                                onActions = {
                                    actionsFor = row
                                    sheetOpen = true
                                },
                                onCopied = model::copied,
                            )
                            QeetrixSeparator(
                                tone =
                                    if (row ==
                                        state.rows.last()
                                    ) {
                                        QeetrixSeparatorTone.Default
                                    } else {
                                        QeetrixSeparatorTone.Subtle
                                    },
                            )
                        }
                    }
                }
            }
        }
        QeetrixSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    actionsFor?.let { row ->
        val live = state.rows.firstOrNull { it.id == row.id } ?: row
        QeetrixBottomSheet(
            open = sheetOpen,
            onOpenChange = { sheetOpen = it },
            title = live.issuer,
            description = live.label.ifBlank { null },
        ) {
            SheetAction(QeetrixIconName.Copy, stringResource(R.string.action_copy_code), onClick = {
                sheetOpen = false
                model.copy(live)
            })
            SheetAction(QeetrixIconName.Pencil, stringResource(R.string.action_rename), onClick = {
                sheetOpen = false
                renaming = live
            })
            QeetrixSeparator()
            SheetAction(QeetrixIconName.Trash, stringResource(R.string.action_delete_account), danger = true, onClick = {
                sheetOpen = false
                deleting = live
            })
        }
    }

    deleting?.let { row ->
        QeetrixConfirmationDialog(
            open = true,
            onOpenChange = { if (!it) deleting = null },
            title = stringResource(R.string.delete_title, row.issuer),
            description = stringResource(R.string.delete_body, row.label.ifBlank { row.issuer }, row.issuer),
            confirmLabel = stringResource(R.string.action_delete_account),
            onConfirm = {
                model.delete(row)
                deleting = null
            },
            intent = QeetrixDialogIntent.Danger,
        )
    }

    renaming?.let { row ->
        RenameDialog(row, onDismiss = { renaming = null }, onSave = { issuer, label ->
            model.rename(row.id, issuer, label)
            renaming = null
        })
    }
}

@Composable
private fun ApprovalsRow(
    state: AccountsState,
    onClick: () -> Unit,
) {
    val link = state.link
    QeetrixListItem(
        title = stringResource(R.string.approvals_row_title),
        description =
            if (link !=
                null
            ) {
                stringResource(R.string.approvals_row_linked, link.deviceName)
            } else {
                stringResource(R.string.approvals_row_not_linked)
            },
        onClick = onClick,
        leading = { QeetrixListItemIcon(QeetrixIconName.CircleCheck) },
        trailing = {
            if (link != null) {
                QeetrixBadge(stringResource(R.string.badge_on), tone = QeetrixBadgeTone.Success)
            } else {
                QeetrixBadge(stringResource(R.string.badge_off))
            }
        },
    )
}

/**
 * An account: its initial, the issuer and account, and the code with its countdown (Qeetrix's one-time code, which
 * copies on tap, marked sensitive). A long press — or TalkBack's action — opens its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRowItem(
    row: AccountRow,
    hidden: Boolean,
    selected: Boolean,
    onActions: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val actionsLabel = stringResource(R.string.account_actions)
    val name = row.issuer.ifBlank { stringResource(R.string.account_unnamed) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) QeetrixTheme.colors.bgSelected else QeetrixTheme.colors.surfaceRaised)
            .combinedClickable(onClickLabel = actionsLabel, onLongClickLabel = actionsLabel, onLongClick = onActions, onClick = onActions)
            .semantics { customActions = listOf(CustomAccessibilityAction(actionsLabel) { onActions().let { true } }) }
            .padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetMd)
            .testTag("account-${row.id}"),
        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        verticalAlignment = Alignment.Top,
    ) {
        LetterTile(initialOf(name))
        Column(Modifier.weight(1f)) {
            QeetrixText(name, weight = QeetrixTextWeight.Medium)
            if (row.label.isNotBlank()) QeetrixText(row.label, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle, maxLines = 1)
            val code = row.code
            if (code != null) {
                QeetrixOTPCode(
                    code = code,
                    expiresAt = Instant.ofEpochMilli(row.expiresAtMillis),
                    period = Duration.ofSeconds(row.periodSeconds.toLong()),
                    label = listOf(name, row.label).filter { it.isNotBlank() }.joinToString(", "),
                    hidden = hidden,
                    size = QeetrixOTPCodeSize.Md,
                    onCopied = onCopied,
                )
            }
        }
    }
}

@Composable
private fun EmptyAccounts(
    linked: Boolean,
    onScan: () -> Unit,
    onTypeKey: () -> Unit,
    onLink: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = QeetrixSpace.LayoutLg), contentAlignment = Alignment.Center) {
        QeetrixEmptyState(
            stringResource(R.string.accounts_empty_title),
            icon = QeetrixIconName.Clock,
            description = stringResource(R.string.accounts_empty_body),
            actions = {
                QeetrixButton(stringResource(R.string.accounts_scan_qr), onClick = onScan, icon = QeetrixIconName.Camera)
                QeetrixButton(stringResource(R.string.accounts_type_key), onClick = onTypeKey, variant = QeetrixButtonVariant.Ghost)
            },
            footer =
                if (linked) {
                    null
                } else {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QeetrixText(
                                stringResource(R.string.accounts_using_qeet_id),
                                size = QeetrixTextSize.Sm,
                                tone = QeetrixTextTone.Subtle,
                            )
                            QeetrixButton(
                                stringResource(R.string.accounts_link_phone),
                                onClick = onLink,
                                variant = QeetrixButtonVariant.Ghost,
                                size = QeetrixButtonSize.Sm,
                            )
                        }
                    }
                },
        )
    }
}

/** Screen 26: rename, on this phone only. */
@Composable
private fun RenameDialog(
    row: AccountRow,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val service = rememberTextFieldState(row.issuer)
    val account = rememberTextFieldState(row.label)
    var error by remember { mutableStateOf<String?>(null) }
    val required = stringResource(R.string.rename_service_required)
    QeetrixDialog(
        open = true,
        onOpenChange = { if (!it) onDismiss() },
        title = stringResource(R.string.rename_title),
        description = stringResource(R.string.rename_body),
        footer = {
            QeetrixButton(stringResource(R.string.action_cancel), onClick = onDismiss, variant = QeetrixButtonVariant.Ghost)
            QeetrixButton(stringResource(R.string.action_save), onClick = {
                val issuer = service.text.toString().trim()
                if (issuer.isEmpty()) {
                    error = required
                } else {
                    onSave(issuer, account.text.toString().trim())
                }
            })
        },
    ) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        ) {
            QeetrixTextField(service, stringResource(R.string.rename_service), error = error)
            QeetrixTextField(account, stringResource(R.string.rename_account))
        }
    }
}
