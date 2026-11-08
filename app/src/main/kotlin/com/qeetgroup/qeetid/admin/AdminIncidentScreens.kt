package com.qeetgroup.qeetid.admin

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.settings.SettingsGroup
import com.qeetgroup.qeetid.settings.SettingsRow
import com.qeetgroup.qeetid.settings.SettingsRule
import com.qeetgroup.qeetid.ui.AdminEventRoute
import com.qeetgroup.qeetid.ui.AdminSuspend
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.Fact
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.MarkRings
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.RingsSize
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.StopFact
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import kotlinx.coroutines.launch

/**
 * Stop all AI agents: the organisation's kill switch, for an agent misbehaving with no time to find which. What stops
 * (every agent's tokens, at once) and what doesn't (people), red, alone on its screen and behind the phone's own check.
 */
@Composable
fun AdminAgentsRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val verification = LocalUserVerification.current
    val scope = rememberActions()
    val short = isShort()
    val summary by admin.summary.collectAsStateWithLifecycle()
    val organization = summary?.organizationName.orEmpty()
    val loader =
        viewModel(key = "admin-agents") {
            Loader {
                admin.inOrganization {
                    access,
                    tenant,
                    ->
                    admin.api.runningAgents(access, tenant)
                }
            }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    var stopped by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<AdminResult<*>?>(null) }
    val noLock = stringResource(R.string.admin_needs_screen_lock)
    val notConfirmed = stringResource(R.string.admin_not_confirmed)
    val running = (state as? AdminResult.Ok)?.value

    stopped?.let { count ->
        ResultPage(
            title = stringResource(R.string.admin_agents_stopped),
            body = pluralStringResource(R.plurals.admin_agents_stopped_body, count, count),
            sealTone = SealTone.Danger,
            seal = { AppIcon(AppIconName.BotOff, null, Modifier.fillMaxSize()) },
            actions = {
                QeetrixButton(stringResource(R.string.action_done), onClick = {
                    navController.popBackStack()
                }, size = QeetrixButtonSize.Lg, fullWidth = true)
            },
        ) { AuditNote() }
        return
    }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.admin_agents), onBack = { navController.popBackStack() }) },
        actions = {
            when {
                running == null -> {}

                running == 0 -> {
                    QeetrixButton(stringResource(R.string.action_done), onClick = {
                        navController.popBackStack()
                    }, size = QeetrixButtonSize.Lg, fullWidth = true)
                }

                else -> {
                    QeetrixButton(
                        pluralStringResource(R.plurals.admin_agents_stop, running, running),
                        onClick = {
                            busy = true
                            problem = null
                            failure = null
                            scope.launch {
                                val prompt =
                                    VerifyPrompt(
                                        container.context.getString(R.string.admin_agents),
                                        container.context.resources.getQuantityString(
                                            R.plurals.admin_agents_in,
                                            running,
                                            running,
                                            organization,
                                        ),
                                    )
                                when (verification.verify(prompt)) {
                                    VerifyResult.Verified -> {
                                        when (
                                            val result =
                                                admin.inOrganization {
                                                    access,
                                                    tenant,
                                                    ->
                                                    admin.api.stopAllAgents(access, tenant)
                                                }
                                        ) {
                                            is AdminResult.Ok -> stopped = result.value
                                            AdminResult.SignedOut -> navController.toAdminSignIn()
                                            else -> failure = result
                                        }
                                    }

                                    VerifyResult.Unavailable -> {
                                        problem = noLock
                                    }

                                    VerifyResult.Failed -> {
                                        problem = notConfirmed
                                    }

                                    VerifyResult.Cancelled -> {}
                                }
                                busy = false
                            }
                        },
                        tone = QeetrixButtonTone.Danger,
                        size = QeetrixButtonSize.Lg,
                        loading = busy,
                        fullWidth = true,
                    )
                }
            }
        },
    ) {
        AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { count ->
            (problem ?: failure?.let { problemText(it) })?.let {
                QeetrixAlert(title = stringResource(R.string.admin_problem_title), description = it, tone = QeetrixAlertTone.Danger)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            MarkRings(
                if (short) RingsSize.Xs else RingsSize.Sm,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                sealTone = SealTone.Danger,
                seal = { AppIcon(AppIconName.BotOff, null, Modifier.fillMaxSize()) },
            )
            Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else 20.dp))
            if (count == 0) {
                PageTitle(stringResource(R.string.admin_agents_none), stringResource(R.string.admin_agents_none_body), centred = true)
            } else {
                PageTitle(stringResource(R.string.admin_agents_heading, organization), null, centred = true)
                Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                Panel {
                    StopFact(pluralStringResource(R.plurals.admin_agents_tokens, count, count))
                    Fact(stringResource(R.string.admin_agents_people))
                    Fact(stringResource(R.string.admin_agents_back))
                }
            }
        }
    }
}

/** Critical events: the last day's high and medium events, newest first, read only. */
@Composable
fun AdminEventsRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val loader =
        viewModel(key = "admin-events") {
            Loader {
                admin.inOrganization {
                    access,
                    tenant,
                    ->
                    admin.api.criticalEvents(access, tenant)
                }
            }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    Screen(
        topBar = {
            ScreenBar(stringResource(R.string.admin_events), onBack = {
                navController.popBackStack()
            }, description = stringResource(R.string.admin_events_window))
        },
    ) {
        GroupsColumn {
            AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { page ->
                if (page.events.isEmpty()) {
                    QeetrixEmptyState(
                        stringResource(R.string.admin_events_none),
                        icon = QeetrixIconName.CircleCheck,
                        description = stringResource(R.string.admin_events_none_body),
                    )
                } else {
                    SettingsGroup(stringResource(R.string.admin_events_list)) {
                        page.events.sortedByDescending { millis(it.occurredAt) ?: 0L }.forEachIndexed { index, event ->
                            if (index > 0) SettingsRule()
                            SettingsRow(
                                label = eventTitle(event),
                                detail =
                                    listOfNotNull(
                                        event.actorEmail,
                                        ago(event.occurredAt, container.now()),
                                    ).joinToString(" · ").ifEmpty { null },
                                start = { LevelBadge(event) },
                                onClick = { navController.navigate(AdminEventRoute(event.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One critical event, read only: who, where, when and why it counts, then the two next steps: open it in the console,
 * or suspend the person.
 */
@Composable
fun AdminEventScreenRoute(
    navController: NavHostController,
    id: String,
) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val context = LocalContext.current
    val loader =
        viewModel(key = "admin-event-$id") {
            Loader {
                admin.inOrganization { access, tenant -> admin.api.criticalEvents(access, tenant) }.then { page ->
                    page.events.firstOrNull { it.id == id }?.let { AdminResult.Ok(it) } ?: AdminResult.NotFound
                }
            }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    val event = (state as? AdminResult.Ok)?.value
    val console = consoleBase(container.api.baseUrl)

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.admin_event_title), onBack = { navController.popBackStack() }) },
        actions = {
            if (event != null) {
                val person = event.personId
                if (person != null && event.actorEmail != null) {
                    QeetrixButton(
                        stringResource(R.string.admin_suspend_action, firstName(event.actorEmail)),
                        onClick = { navController.navigate(AdminSuspend(person)) },
                        variant = QeetrixButtonVariant.Ghost,
                        tone = QeetrixButtonTone.Neutral,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                }
                QeetrixButton(
                    stringResource(R.string.admin_open_console),
                    onClick = {
                        val path =
                            if (event.source ==
                                CriticalEvent.ANOMALY
                            ) {
                                "/security/threats/anomalies"
                            } else {
                                "/security/audit-logs?event=${event.id}"
                            }
                        openOutside(context, console + path)
                    },
                    size = QeetrixButtonSize.Lg,
                    fullWidth = true,
                )
            }
        },
    ) {
        AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { found ->
            LevelBadge(found)
            Spacer(Modifier.height(QeetrixSpace.GapSm))
            QeetrixHeading(
                eventTitle(found),
                level = 1,
                size = if (isShort()) QeetrixHeadingSize.HeadingSm else QeetrixHeadingSize.HeadingMd,
            )
            eventReasons(found)?.let {
                Spacer(Modifier.height(QeetrixSpace.GapXs))
                QeetrixText(it, tone = QeetrixTextTone.Subtle)
            }
            Spacer(Modifier.height(if (isShort()) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
            FactRows(
                listOfNotNull(
                    found.actorEmail?.let { stringResource(R.string.admin_event_person) to it },
                    listOfNotNull(found.meta("location"), found.ip).joinToString(" · ").ifEmpty { null }?.let {
                        stringResource(R.string.admin_event_where) to
                            it
                    },
                    millis(found.occurredAt)?.let { stringResource(R.string.admin_event_when) to whenText(context, it) },
                    found.meta("user_agent")?.let { stringResource(R.string.admin_event_session) to it },
                ),
            )
        }
    }
}

@Composable
private fun LevelBadge(event: CriticalEvent) {
    if (event.high) {
        QeetrixBadge(stringResource(R.string.admin_level_high), tone = QeetrixBadgeTone.Danger)
    } else {
        QeetrixBadge(stringResource(R.string.admin_level_medium), tone = QeetrixBadgeTone.Warning)
    }
}

/** What happened, in words: "Person suspended", "Unusual activity". */
@Composable
internal fun eventTitle(event: CriticalEvent): String {
    if (event.source == CriticalEvent.ANOMALY) return stringResource(R.string.admin_event_anomaly)
    val title =
        when (event.action) {
            "agent.kill_switch" -> R.string.admin_event_agents_stopped
            "user.suspended" -> R.string.admin_event_suspended
            "tenant.suspended" -> R.string.admin_event_org_suspended
            "auth.account_locked" -> R.string.admin_event_account_locked
            "auth.mfa_locked" -> R.string.admin_event_mfa_locked
            "mfa.admin_reset" -> R.string.admin_event_mfa_reset
            "session.admin_revoked_all" -> R.string.admin_event_signed_out
            "role.assigned" -> R.string.admin_event_role
            "user.reactivated" -> R.string.admin_event_reactivated
            "mfa.totp_disabled" -> R.string.admin_event_totp_off
            "risk.settings_updated" -> R.string.admin_event_risk
            else -> null
        }
    return title?.let { stringResource(it) } ?: event.action
}

/** Why an anomaly stood out: "A new IP address, at an unusual hour". */
@Composable
private fun eventReasons(event: CriticalEvent): String? {
    val words =
        event.reasons.mapNotNull { reason ->
            when (reason) {
                "new_ip" -> stringResource(R.string.admin_reason_new_ip)
                "unusual_hour" -> stringResource(R.string.admin_reason_unusual_hour)
                "new_action_type" -> stringResource(R.string.admin_reason_new_action)
                else -> null
            }
        }
    return words.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar { it.uppercase() }
}

private fun whenText(
    context: Context,
    millis: Long,
): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
