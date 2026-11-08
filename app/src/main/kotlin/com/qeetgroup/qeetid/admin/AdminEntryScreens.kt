package com.qeetgroup.qeetid.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.settings.SettingsGroup
import com.qeetgroup.qeetid.settings.SettingsRow
import com.qeetgroup.qeetid.settings.SettingsRule
import com.qeetgroup.qeetid.ui.AdminAgents
import com.qeetgroup.qeetid.ui.AdminEvents
import com.qeetgroup.qeetid.ui.AdminHome
import com.qeetgroup.qeetid.ui.AdminOrganizations
import com.qeetgroup.qeetid.ui.AdminPeople
import com.qeetgroup.qeetid.ui.AdminRequests
import com.qeetgroup.qeetid.ui.AdminSignIn
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.Fact
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalPasskeys
import com.qeetgroup.qeetid.ui.MarkRings
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.RingsSize
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Back to the start of administration when the session has ended: sign in again, with Settings behind. */
internal fun NavHostController.toAdminSignIn() = navigate(AdminSignIn) { popUpTo(Settings) }

/** Admin home for the chosen organisation, with Settings behind it. */
internal fun NavHostController.toAdminHome() = navigate(AdminHome) { popUpTo(Settings) }

/**
 * Admin sign-in: what administration on the phone is for (a few urgent actions, not the console), then a passkey
 * sign-in, as on the computer. One organisation goes straight to Admin home; more goes to Choose organisation.
 */
@Composable
fun AdminSignInRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val passkeys = LocalPasskeys.current
    val scope = rememberActions()
    val device = deviceWord()
    val short = isShort()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<SignInOutcome?>(null) }

    fun signIn() {
        busy = true
        problem = null
        scope.launch {
            val outcome = container.admin.signIn(passkeys)
            when (outcome) {
                is SignInOutcome.SignedIn -> {
                    val only = outcome.organizations.singleOrNull()
                    if (only != null && container.admin.choose(only) is AdminResult.Ok) {
                        navController.toAdminHome()
                    } else {
                        navController.navigate(AdminOrganizations) { popUpTo(Settings) }
                    }
                }

                SignInOutcome.Cancelled -> {}

                else -> {
                    problem = outcome
                }
            }
            busy = false
        }
    }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.admin_title), onBack = { navController.popBackStack() }) },
        actions = {
            QeetrixButton(
                stringResource(R.string.action_not_now),
                onClick = { navController.popBackStack() },
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
                size = QeetrixButtonSize.Lg,
                fullWidth = true,
            )
            QeetrixButton(
                stringResource(R.string.admin_sign_in),
                onClick = ::signIn,
                size = QeetrixButtonSize.Lg,
                loading = busy,
                fullWidth = true,
            )
        },
    ) {
        problem?.let { outcome ->
            QeetrixAlert(title = signInProblemTitle(outcome), description = signInProblemBody(outcome), tone = QeetrixAlertTone.Danger)
            Spacer(Modifier.height(QeetrixSpace.InsetLg))
        }
        MarkRings(
            if (short) RingsSize.Xs else RingsSize.Sm,
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            seal = { QeetrixIcon(QeetrixIconName.ShieldCheck, null, Modifier.fillMaxSize()) },
        )
        Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else 20.dp))
        PageTitle(stringResource(R.string.admin_sign_in_heading), stringResource(R.string.admin_sign_in_body), centred = true)
        Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
        Panel {
            Fact(stringResource(R.string.admin_sign_in_passkey))
            Fact(stringResource(R.string.admin_sign_in_check))
            Fact(stringResource(R.string.admin_sign_in_audit, device))
        }
    }
}

@Composable
private fun signInProblemTitle(outcome: SignInOutcome): String =
    stringResource(
        when (outcome) {
            SignInOutcome.NoPasskey -> R.string.admin_no_passkey_title
            SignInOutcome.PasskeyUnavailable -> R.string.admin_passkey_unavailable_title
            SignInOutcome.NotAnAdministrator -> R.string.admin_not_admin_title
            else -> R.string.admin_sign_in_failed_title
        },
    )

@Composable
private fun signInProblemBody(outcome: SignInOutcome): String =
    when (outcome) {
        SignInOutcome.NoPasskey -> stringResource(R.string.admin_no_passkey_body, deviceWord())
        SignInOutcome.PasskeyUnavailable -> stringResource(R.string.admin_passkey_unavailable_body, deviceWord())
        SignInOutcome.NotAnAdministrator -> stringResource(R.string.admin_not_admin_body)
        SignInOutcome.Network -> stringResource(R.string.admin_problem_network)
        else -> stringResource(R.string.admin_problem_refused)
    }

/** Choose organisation: the ones the person has a role in, each with their role; choosing one scopes the session. */
@Composable
fun AdminOrganizationsRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberActions()
    val summary by container.admin.summary.collectAsStateWithLifecycle()
    val loader = viewModel(key = "admin-orgs") { Loader { container.admin.organizations() } }
    val state by loader.state.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<AdminResult<*>?>(null) }

    Screen(topBar = { ScreenBar(stringResource(R.string.admin_choose_org), onBack = { navController.popBackStack() }) }) {
        GroupsColumn {
            summary?.email?.takeIf { it.isNotBlank() }?.let {
                QeetrixText(
                    stringResource(R.string.admin_signed_in_as, it),
                    modifier = Modifier.padding(start = QeetrixSpace.InsetLg, end = QeetrixSpace.InsetLg, bottom = QeetrixSpace.InsetLg),
                    tone = QeetrixTextTone.Subtle,
                )
            }
            failed?.let {
                Box(Modifier.padding(start = QeetrixSpace.InsetLg, end = QeetrixSpace.InsetLg, bottom = QeetrixSpace.InsetLg)) {
                    QeetrixAlert(
                        title = stringResource(R.string.admin_problem_title),
                        description = problemText(it),
                        tone = QeetrixAlertTone.Danger,
                    )
                }
            }
            AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { organizations ->
                SettingsGroup(stringResource(R.string.admin_you_manage)) {
                    organizations.forEachIndexed { index, organization ->
                        if (index > 0) SettingsRule()
                        SettingsRow(
                            label = organization.name,
                            detail = organization.roles.joinToString(", "),
                            start = { Initials(organization.name, brand = true) },
                            onClick =
                                {
                                    if (choosing == null) {
                                        choosing = organization.id
                                        failed = null
                                        scope.launch {
                                            when (val chosen = container.admin.choose(organization)) {
                                                is AdminResult.Ok -> navController.toAdminHome()
                                                AdminResult.SignedOut -> navController.toAdminSignIn()
                                                else -> failed = chosen
                                            }
                                            choosing = null
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** What Admin home counts; null where this administrator may not see it. */
internal data class HomeCounts(
    val requests: Int?,
    val events: Int?,
    val high: Int?,
    val agents: Int?,
)

private suspend fun homeCounts(container: AppContainer): AdminResult<HomeCounts> =
    coroutineScope {
        val admin = container.admin
        val requests = async { admin.inOrganization { access, tenant -> admin.api.accessRequests(access, tenant) } }
        val events = async { admin.inOrganization { access, tenant -> admin.api.criticalEvents(access, tenant) } }
        val agents = async { admin.inOrganization { access, tenant -> admin.api.runningAgents(access, tenant) } }
        val r = requests.await()
        val e = events.await()
        val a = agents.await()
        when {
            listOf(r, e, a).any { it == AdminResult.SignedOut } -> {
                AdminResult.SignedOut
            }

            r == AdminResult.Network && e == AdminResult.Network -> {
                AdminResult.Network
            }

            else -> {
                val page = (e as? AdminResult.Ok)?.value
                AdminResult.Ok(
                    HomeCounts((r as? AdminResult.Ok)?.value?.pending, page?.events?.size, page?.high, (a as? AdminResult.Ok)?.value),
                )
            }
        }
    }

/**
 * Admin home: the organisation in the bar with Switch, then only what needs a person now (access requests, critical
 * events) and, apart under their own title, the two emergency actions.
 */
@Composable
fun AdminHomeRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberActions()
    val summary by container.admin.summary.collectAsStateWithLifecycle()
    val loader = viewModel(key = "admin-home-${summary?.organizationId}") { Loader { homeCounts(container) } }
    val state by loader.state.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (shown) loader.reload(quietly = true)
        shown = true
        onPauseOrDispose {}
    }
    LaunchedEffect(summary) { if (summary == null) navController.toAdminSignIn() }

    Screen(
        topBar = {
            ScreenBar(
                summary?.organizationName ?: stringResource(R.string.admin_title),
                onBack = { navController.popBackStack() },
                description = stringResource(R.string.admin_title),
                actions = {
                    QeetrixButton(
                        stringResource(R.string.admin_switch),
                        onClick = { navController.navigate(AdminOrganizations) },
                        variant = QeetrixButtonVariant.Ghost,
                        size = QeetrixButtonSize.Sm,
                        modifier = Modifier.padding(end = QeetrixSpace.InsetSm),
                    )
                },
            )
        },
    ) {
        GroupsColumn {
            AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { counts ->
                SettingsGroup(stringResource(R.string.admin_needs_you)) {
                    SettingsRow(
                        label = stringResource(R.string.admin_requests),
                        detail = stringResource(R.string.admin_requests_body),
                        end = {
                            counts.requests?.let {
                                QeetrixBadge(
                                    it.toString(),
                                    tone =
                                        if (it >
                                            0
                                        ) {
                                            QeetrixBadgeTone.Warning
                                        } else {
                                            QeetrixBadgeTone.Neutral
                                        },
                                )
                            }
                        },
                        onClick = { navController.navigate(AdminRequests) },
                    )
                    SettingsRule()
                    SettingsRow(
                        label = stringResource(R.string.admin_events),
                        detail = stringResource(R.string.admin_events_body),
                        end = {
                            val high = counts.high ?: 0
                            when {
                                high > 0 -> {
                                    QeetrixBadge(
                                        pluralStringResource(R.plurals.admin_events_high, high, high),
                                        tone = QeetrixBadgeTone.Danger,
                                    )
                                }

                                counts.events != null -> {
                                    QeetrixBadge(counts.events.toString(), tone = QeetrixBadgeTone.Neutral)
                                }
                            }
                        },
                        onClick = { navController.navigate(AdminEvents) },
                    )
                }
                SettingsGroup(stringResource(R.string.admin_emergency)) {
                    SettingsRow(
                        label = stringResource(R.string.admin_suspend),
                        detail = stringResource(R.string.admin_suspend_body),
                        onClick = { navController.navigate(AdminPeople) },
                    )
                    SettingsRule()
                    SettingsRow(
                        label = stringResource(R.string.admin_agents),
                        detail = counts.agents?.let { pluralStringResource(R.plurals.admin_agents_running, it, it) },
                        danger = true,
                        onClick = { navController.navigate(AdminAgents) },
                    )
                }
                SettingsGroup(stringResource(R.string.admin_session)) {
                    SettingsRow(
                        label = stringResource(R.string.admin_sign_out),
                        detail = summary?.email?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.admin_signed_in_as, it) },
                        danger = true,
                        onClick = {
                            scope.launch {
                                container.admin.signOut()
                                navController.popBackStack(Settings, inclusive = false)
                            }
                        },
                    )
                }
            }
        }
    }
}
