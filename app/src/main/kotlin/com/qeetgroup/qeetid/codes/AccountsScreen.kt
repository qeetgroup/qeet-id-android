package com.qeetgroup.qeetid.codes

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.system.CodeClipboard
import com.qeetgroup.qeetid.system.NotificationAccess
import com.qeetgroup.qeetid.ui.LetterTile
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.ScanAccount
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetid.ui.TypeKey
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.initialOf
import com.qeetgroup.qeetid.ui.isTablet
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertVariant
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeVariant
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.button.QeetrixIconButton
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialog
import com.qeetgroup.qeetrix.components.dialog.QeetrixDialogIntent
import com.qeetgroup.qeetrix.components.drawer.QeetrixActionSheet
import com.qeetgroup.qeetrix.components.drawer.QeetrixBottomSheet
import com.qeetgroup.qeetrix.components.drawer.QeetrixSheetAction
import com.qeetgroup.qeetrix.components.drawer.QeetrixSheetActionSeparator
import com.qeetgroup.qeetrix.components.dropdownmenu.QeetrixMenuItemTone
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
import com.qeetgroup.qeetrix.components.typography.QeetrixLink
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
    var addOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountRow?>(null) }
    var renaming by remember { mutableStateOf<AccountRow?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(NotificationAccess.enabled(context)) }
    val tablet = isTablet()
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
        // Shown in the screen's own scope: clearing the marker re-keys this effect, which must not cancel it.
        scope.launch { snackbars.show(resources.getString(R.string.account_added, issuer), QeetrixSnackbarTone.Success) }
        onAddedShown()
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
                            pluralStringResource(R.plurals.accounts_count, state.total, state.total, deviceWord())
                        },
                    size = QeetrixTopAppBarSize.Large,
                    scrollState = barScroll,
                    actions = {
                        if (state.total > 0) {
                            QeetrixIconButton(
                                QeetrixIconName.Plus,
                                stringResource(R.string.accounts_add),
                                onClick = { addOpen = true },
                            )
                        }
                        QeetrixIconButton(
                            QeetrixIconName.Settings,
                            stringResource(R.string.accounts_settings),
                            onClick = onSettings,
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
                                modifier = Modifier.problem(),
                            )
                        }
                    }
                    if (linked && !notificationsOn) {
                        item(key = "notifications-off") {
                            QeetrixAlert(
                                title = stringResource(R.string.alert_notifications_off_title, deviceWord()),
                                description = stringResource(R.string.alert_notifications_off_body),
                                tone = QeetrixAlertTone.Warning,
                                modifier = Modifier.problem(),
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
                                        variant = QeetrixButtonVariant.Soft,
                                        size = QeetrixButtonSize.Sm,
                                    )
                                },
                            )
                        }
                    }
                    if (state.unlinkedByQeetId) {
                        item(key = "unlinked") {
                            QeetrixAlert(
                                title = stringResource(R.string.alert_unlinked_title, deviceWord()),
                                description = stringResource(R.string.alert_unlinked_body),
                                tone = QeetrixAlertTone.Warning,
                                modifier = Modifier.problem(),
                                actions = {
                                    QeetrixButton(
                                        stringResource(R.string.alert_unlinked_action),
                                        onClick = onLink,
                                        variant = QeetrixButtonVariant.Soft,
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
                                tone = QeetrixAlertTone.Info,
                                modifier = Modifier.problem(),
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
                    // While searching, only the accounts: no approvals row and no Codes heading, as the mockup shows.
                    if (state.query.isBlank()) {
                        item(key = "approvals") {
                            Column(Modifier.background(QeetrixTheme.colors.surfaceRaised)) {
                                ApprovalsRow(state, onClick = if (linked) onSettings else onLink)
                                QeetrixSeparator(tone = QeetrixSeparatorTone.Default)
                            }
                        }
                        item(key = "codes-header") { QeetrixSectionHeader(stringResource(R.string.accounts_codes)) }
                    }
                    if (state.rows.isEmpty()) {
                        item(key = "no-match") { NoMatch(state.query, onAdd = { addOpen = true }) }
                    }
                    if (tablet) {
                        // Tablets: two columns of 4 dp cards, as on the mockup.
                        items(state.rows.chunked(2), key = { pair -> pair.first().id }) { pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.GapSm),
                                horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
                            ) {
                                for (row in pair) {
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .border(1.dp, QeetrixTheme.colors.borderDefault, QeetrixShapes.Surface)
                                            .clip(QeetrixShapes.Surface),
                                    ) {
                                        AccountRowItem(
                                            row = row,
                                            hidden = state.hideCodes,
                                            selected = sheetOpen && actionsFor?.id == row.id,
                                            onActions = {
                                                actionsFor = row
                                                sheetOpen = true
                                            },
                                            onCopied = { model.copied() },
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (!tablet) {
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
                                    onCopied = { model.copied() },
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
        }
        QeetrixSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    actionsFor?.let { row ->
        val live = state.rows.firstOrNull { it.id == row.id } ?: row
        // The account's actions: Qeetrix's action sheet, headed by the account. Choosing one closes it first.
        QeetrixActionSheet(
            open = sheetOpen,
            onOpenChange = { sheetOpen = it },
            title = live.issuer,
            description = live.label.ifBlank { null },
        ) {
            QeetrixSheetAction(stringResource(R.string.action_copy_code), onClick = { model.copy(live) }, icon = QeetrixIconName.Copy)
            QeetrixSheetAction(stringResource(R.string.action_rename), onClick = { renaming = live }, icon = QeetrixIconName.Pencil)
            QeetrixSheetActionSeparator()
            QeetrixSheetAction(
                stringResource(R.string.action_remove, deviceWord()),
                onClick = { deleting = live },
                icon = QeetrixIconName.Trash,
                tone = QeetrixMenuItemTone.Danger,
            )
        }
    }

    // Add an account: the two ways in, each said in a line, and that codes stay on the phone.
    QeetrixActionSheet(
        open = addOpen,
        onOpenChange = { addOpen = it },
        title = stringResource(R.string.add_title),
        description = stringResource(R.string.add_subtitle),
    ) {
        QeetrixSheetAction(
            stringResource(R.string.add_scan),
            onClick = onAdd,
            icon = QeetrixIconName.QrCode,
            description = stringResource(R.string.add_scan_note),
        )
        QeetrixSheetAction(
            stringResource(R.string.add_type),
            onClick = onTypeKey,
            icon = QeetrixIconName.Keyboard,
            description = stringResource(R.string.add_type_note),
        )
        QeetrixSheetActionSeparator()
        Row(
            Modifier.padding(horizontal = QeetrixSpace.InsetXl, vertical = QeetrixSpace.InsetMd),
            horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm),
        ) {
            QeetrixIcon(
                QeetrixIconName.Lock,
                null,
                Modifier.padding(top = 2.dp),
                size = QeetrixSize.IconSm,
                tint = QeetrixTheme.colors.iconSubtle,
            )
            QeetrixText(stringResource(R.string.add_stays, deviceWord()), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }

    deleting?.let { row ->
        RemoveDialog(row, onDismiss = { deleting = null }, onRemove = {
            model.delete(row)
            deleting = null
        })
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
    val colors = QeetrixTheme.colors
    QeetrixListItem(
        title = stringResource(R.string.approvals_row_title),
        description =
            when {
                link == null -> stringResource(R.string.approvals_row_not_linked, deviceWord())
                link.accountEmail != null -> stringResource(R.string.approvals_row_for, link.accountEmail)
                else -> stringResource(R.string.approvals_row_linked, link.deviceName)
            },
        onClick = onClick,
        leading = {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (link != null) colors.bgSuccessSubtle else colors.bgNeutralSubtle, QeetrixShapes.Control),
                contentAlignment = Alignment.Center,
            ) {
                QeetrixIcon(QeetrixIconName.ShieldCheck, null, tint = if (link != null) colors.iconSuccess else colors.iconSubtle)
            }
        },
        trailing = {
            if (link != null) {
                QeetrixBadge(stringResource(R.string.badge_on), tone = QeetrixBadgeTone.Success)
            } else {
                QeetrixBadge(
                    stringResource(R.string.approvals_row_link),
                    tone = QeetrixBadgeTone.Brand,
                    variant = QeetrixBadgeVariant.Solid,
                )
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
    // Qeetrix's list row: the initial's tile, the site and account, and the code with its countdown at the end in the
    // row's content slot (its own element: a tap copies it, and Qeetrix clears the clipboard 30 s later). Pressing the
    // row, or holding it, opens the account's actions.
    QeetrixListItem(
        title = name,
        modifier =
            Modifier
                .semantics { customActions = listOf(CustomAccessibilityAction(actionsLabel) { onActions().let { true } }) }
                .testTag("account-${row.id}"),
        description = row.label.ifBlank { null },
        selected = selected,
        onClick = onActions,
        onLongClick = onActions,
        onLongClickLabel = actionsLabel,
        content =
            row.code?.let { code ->
                {
                    QeetrixOTPCode(
                        code = code,
                        expiresAt = Instant.ofEpochMilli(row.expiresAtMillis),
                        period = Duration.ofSeconds(row.periodSeconds.toLong()),
                        label = listOf(name, row.label).filter { it.isNotBlank() }.joinToString(", "),
                        hidden = hidden,
                        size = QeetrixOTPCodeSize.Md,
                        // The app says "Copied" itself, with when it clears.
                        confirm = false,
                        clearAfter = Duration.ofMillis(CodeClipboard.CLEAR_AFTER_MILLIS),
                        countdownAtEnd = true,
                        onCopied = onCopied,
                    )
                }
            },
        leading = { QeetrixListItemIcon(initialOf(name)) },
    )
}

@Composable
private fun EmptyAccounts(
    linked: Boolean,
    onScan: () -> Unit,
    onTypeKey: () -> Unit,
    onLink: () -> Unit,
) {
    val colors = QeetrixTheme.colors
    Column(
        Modifier.readingColumn().padding(horizontal = QeetrixSpace.InsetXl, vertical = QeetrixSpace.LayoutMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(colors.bgBrandSubtle, QeetrixShapes.Control), contentAlignment = Alignment.Center) {
            QeetrixIcon(QeetrixIconName.QrCode, null, size = 28.dp, tint = colors.iconBrand)
        }
        Spacer(Modifier.height(QeetrixSpace.InsetLg))
        PageTitle(stringResource(R.string.accounts_empty_title), stringResource(R.string.accounts_empty_body), centred = true)
        Spacer(Modifier.height(QeetrixSpace.LayoutSm))
        QeetrixButton(
            stringResource(R.string.accounts_scan_qr),
            onClick = onScan,
            size = QeetrixButtonSize.Lg,
            icon = QeetrixIconName.QrCode,
            fullWidth = true,
        )
        Spacer(Modifier.height(QeetrixSpace.GapSm))
        QeetrixButton(
            stringResource(R.string.accounts_type_key),
            onClick = onTypeKey,
            variant = QeetrixButtonVariant.Ghost,
            size = QeetrixButtonSize.Lg,
            icon = QeetrixIconName.Keyboard,
            fullWidth = true,
        )
        if (!linked) {
            Spacer(Modifier.height(QeetrixSpace.InsetLg))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapXs)) {
                QeetrixText(stringResource(R.string.accounts_using_qeet_id), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
                QeetrixLink(stringResource(R.string.accounts_link_phone, deviceWord()), onClick = onLink, size = QeetrixTextSize.Sm)
            }
        }
    }
}

/**
 * Remove from this phone: Qeetrix's danger dialog, the question as its title, what stops, and how not to get locked out
 * of the site; Cancel, and a red Remove.
 */
@Composable
private fun RemoveDialog(
    row: AccountRow,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = QeetrixTheme.colors
    val site = row.issuer.ifBlank { stringResource(R.string.account_unnamed) }
    QeetrixDialog(
        open = true,
        onOpenChange = { if (!it) onDismiss() },
        title = stringResource(R.string.remove_title, site),
        description = stringResource(R.string.remove_body, row.label.ifBlank { site }, deviceWord()),
        intent = QeetrixDialogIntent.Danger,
        footer = {
            QeetrixButton(
                stringResource(R.string.action_cancel),
                onClick = onDismiss,
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
            )
            QeetrixButton(stringResource(R.string.action_remove_short), onClick = onRemove, tone = QeetrixButtonTone.Danger)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
            QeetrixIcon(
                QeetrixIconName.TriangleAlert,
                null,
                Modifier.padding(top = 2.dp),
                size = QeetrixSize.IconSm,
                tint = colors.iconWarning,
            )
            QeetrixText(stringResource(R.string.remove_warning, site), size = QeetrixTextSize.Sm)
        }
    }
}

/**
 * Rename: Qeetrix's dialog with the site's name and the account. The name changes on this phone only and the site isn't
 * told; Save waits for a name.
 */
@Composable
private fun RenameDialog(
    row: AccountRow,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val service = rememberTextFieldState(row.issuer)
    val account = rememberTextFieldState(row.label)
    val empty = service.text.isBlank()
    val site = row.issuer.ifBlank { stringResource(R.string.account_unnamed) }
    QeetrixDialog(
        open = true,
        onOpenChange = { if (!it) onDismiss() },
        title = stringResource(R.string.rename_title),
        description = stringResource(R.string.rename_body, deviceWord(), site),
        footer = {
            QeetrixButton(
                stringResource(R.string.action_cancel),
                onClick = onDismiss,
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
            )
            QeetrixButton(
                stringResource(R.string.action_save),
                onClick = { onSave(service.text.toString().trim(), account.text.toString().trim()) },
                enabled = !empty,
            )
        },
    ) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        ) {
            QeetrixTextField(
                service,
                stringResource(R.string.rename_service),
                error = if (empty) stringResource(R.string.rename_service_required) else null,
                startAdornment = { LetterTile(initialOf(service.text.toString().ifBlank { site }), size = 24.dp) },
            )
            QeetrixTextField(account, stringResource(R.string.rename_account), optional = true)
        }
    }
}

/** A problem at the top of Accounts, as the mockup says it: an in-page alert in the page gutter, above the rest. */
private fun Modifier.problem(): Modifier = padding(start = QeetrixSpace.InsetLg, end = QeetrixSpace.InsetLg, top = QeetrixSpace.InsetLg)

/** Nothing matches the search: say so with the words searched for, and offer to add the account. */
@Composable
private fun NoMatch(
    query: String,
    onAdd: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = QeetrixSpace.InsetLg, end = QeetrixSpace.InsetLg, top = QeetrixSpace.LayoutMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QeetrixText(
            stringResource(R.string.accounts_no_match_title, query.trim()),
            weight = QeetrixTextWeight.Semibold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QeetrixSpace.GapXs))
        QeetrixText(
            stringResource(R.string.accounts_no_match_body),
            size = QeetrixTextSize.Sm,
            tone = QeetrixTextTone.Subtle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QeetrixSpace.InsetLg))
        QeetrixButton(
            stringResource(R.string.accounts_add),
            onClick = onAdd,
            variant = QeetrixButtonVariant.Soft,
            icon = QeetrixIconName.Plus,
        )
    }
}
