package com.qeetgroup.qeetid.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.settings.SettingsGroup
import com.qeetgroup.qeetid.settings.SettingsRow
import com.qeetgroup.qeetid.settings.SettingsRule
import com.qeetgroup.qeetid.ui.AdminSuspend
import com.qeetgroup.qeetid.ui.Fact
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalPasskeys
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.StopFact
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyStateSize
import com.qeetgroup.qeetrix.components.input.QeetrixOption
import com.qeetgroup.qeetrix.components.input.QeetrixSearchField
import com.qeetgroup.qeetrix.components.radiogroup.QeetrixRadioGroup
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Who Find a person lists: before typing, the people in recent critical events and then everyone; else the matches. */
internal data class PeopleList(
    val recent: List<Member>,
    val others: List<Member>,
    val searching: Boolean,
)

internal class PeopleViewModel(
    private val admin: AdminSession,
) : ViewModel() {
    private val _state = MutableStateFlow<AdminResult<PeopleList>?>(null)
    val state: StateFlow<AdminResult<PeopleList>?> = _state.asStateFlow()
    private var job: Job? = null

    init {
        search("")
    }

    fun search(query: String) {
        job?.cancel()
        job =
            viewModelScope.launch {
                _state.value =
                    if (query.isBlank()) {
                        everyone()
                    } else {
                        admin
                            .inOrganization {
                                access,
                                _,
                                ->
                                admin.api.members(access, query)
                            }.map { PeopleList(emptyList(), it, true) }
                    }
            }
    }

    private suspend fun everyone(): AdminResult<PeopleList> =
        coroutineScope {
            val events = async { admin.inOrganization { access, tenant -> admin.api.criticalEvents(access, tenant) } }
            val all = admin.inOrganization { access, _ -> admin.api.members(access, "") }
            if (all !is AdminResult.Ok) return@coroutineScope all.map { PeopleList(emptyList(), emptyList(), false) }
            // The people the last day's critical events are about: the ones an on-call person looks for first.
            val ids =
                (events.await() as? AdminResult.Ok)
                    ?.value
                    ?.events
                    .orEmpty()
                    .mapNotNull { it.personId }
                    .distinct()
                    .take(RECENT)
            val known = all.value.associateBy { it.id }
            val recent =
                ids
                    .map { id ->
                        async {
                            known[id] ?: (
                                admin.inOrganization {
                                    access,
                                    _,
                                    ->
                                    admin.api.member(access, id)
                                } as? AdminResult.Ok
                            )?.value
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .filter { !it.suspended }
            val recentIds = recent.map { it.id }.toSet()
            AdminResult.Ok(PeopleList(recent, all.value.filter { it.id !in recentIds && !it.suspended }, false))
        }

    private companion object {
        const val RECENT = 5
    }
}

/** Find a person: search by name or email; before typing, the people in recent critical events come first. */
@Composable
fun AdminPeopleRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val model = viewModel(key = "admin-people") { PeopleViewModel(container.admin) }
    val state by model.state.collectAsStateWithLifecycle()
    val query = rememberTextFieldState()

    Screen(topBar = { ScreenBar(stringResource(R.string.admin_suspend), onBack = { navController.popBackStack() }) }) {
        GroupsColumn {
            Column(Modifier.readingColumn().padding(horizontal = QeetrixSpace.InsetLg)) {
                QeetrixSearchField(
                    query,
                    label = stringResource(R.string.admin_people_search),
                    placeholder = stringResource(R.string.admin_people_search),
                    onSearch = model::search,
                )
                Spacer(Modifier.height(QeetrixSpace.GapSm))
                QeetrixText(stringResource(R.string.admin_people_hint), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
                Spacer(Modifier.height(20.dp))
            }
            AdminContent(
                state,
                onRetry = { model.search(query.text.toString()) },
                onSignedOut = { navController.toAdminSignIn() },
            ) { people ->
                val open: (Member) -> Unit = { navController.navigate(AdminSuspend(it.id)) }
                when {
                    people.searching && people.others.isEmpty() -> {
                        QeetrixEmptyState(
                            stringResource(R.string.admin_people_none),
                            icon = QeetrixIconName.SearchX,
                            description = stringResource(R.string.admin_people_none_body),
                            size = QeetrixEmptyStateSize.Section,
                        )
                    }

                    people.searching -> {
                        PeopleGroup(stringResource(R.string.admin_people_results), people.others, open)
                    }

                    else -> {
                        if (people.recent.isNotEmpty()) PeopleGroup(stringResource(R.string.admin_people_recent), people.recent, open)
                        if (people.others.isNotEmpty()) PeopleGroup(stringResource(R.string.admin_people_everyone), people.others, open)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleGroup(
    title: String,
    people: List<Member>,
    onOpen: (Member) -> Unit,
) {
    SettingsGroup(title) {
        people.forEachIndexed { index, member ->
            if (index > 0) SettingsRule()
            SettingsRow(
                label = member.name,
                detail = listOfNotNull(member.email.takeIf { it != member.name }, member.team).joinToString(" · ").ifEmpty { null },
                start = { Initials(member.name) },
                onClick = { onOpen(member) },
            )
        }
    }
}

/**
 * Suspend a person: what happens and what doesn't, why (for the audit log), and the phone's own check first, every
 * time. Suspended shows Reactivate right there, for a mistake; reactivating asks for a fresh passkey check, as Qeet ID
 * requires.
 */
@Composable
fun AdminSuspendRoute(
    navController: NavHostController,
    userId: String,
) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val verification = LocalUserVerification.current
    val passkeys = LocalPasskeys.current
    val scope = rememberActions()
    val short = isShort()
    val loader =
        viewModel(key = "admin-member-$userId") {
            Loader {
                admin.inOrganization {
                    access,
                    _,
                    ->
                    admin.api.member(access, userId)
                }
            }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    var reason by remember { mutableStateOf<SuspendReason?>(null) }
    var suspended by remember { mutableStateOf<Boolean?>(null) }
    var reactivated by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val member = (state as? AdminResult.Ok)?.value
    val isSuspended = suspended ?: member?.suspended ?: false
    val reasonLabels = suspendReasonLabels()
    val noLock = stringResource(R.string.admin_needs_screen_lock)
    val notConfirmed = stringResource(R.string.admin_not_confirmed)
    val stepUpFailed = stringResource(R.string.admin_step_up_failed)
    var failure by remember { mutableStateOf<AdminResult<*>?>(null) }

    if (member != null && isSuspended) {
        ResultPage(
            title = stringResource(R.string.admin_suspended_title, member.name),
            body = stringResource(R.string.admin_suspended_body),
            sealTone = SealTone.Danger,
            seal = { QeetrixIcon(QeetrixIconName.Lock, null, Modifier.fillMaxSize()) },
            actions = {
                QeetrixButton(
                    stringResource(R.string.admin_reactivate),
                    onClick = {
                        busy = true
                        problem = null
                        failure = null
                        scope.launch {
                            when (admin.stepUp(passkeys)) {
                                StepUpOutcome.Done -> {
                                    when (val result = admin.inOrganization { access, _ -> admin.api.reactivate(access, member.id) }) {
                                        is AdminResult.Ok, is AdminResult.Conflict -> {
                                            suspended = false
                                            reactivated = true
                                        }

                                        AdminResult.SignedOut -> {
                                            navController.toAdminSignIn()
                                        }

                                        else -> {
                                            failure = result
                                        }
                                    }
                                }

                                StepUpOutcome.Cancelled -> {}

                                StepUpOutcome.SignedOut -> {
                                    navController.toAdminSignIn()
                                }

                                else -> {
                                    problem = stepUpFailed
                                }
                            }
                            busy = false
                        }
                    },
                    variant = QeetrixButtonVariant.Ghost,
                    tone = QeetrixButtonTone.Neutral,
                    size = QeetrixButtonSize.Lg,
                    loading = busy,
                    fullWidth = true,
                )
                QeetrixButton(stringResource(R.string.action_done), onClick = {
                    navController.popBackStack()
                }, size = QeetrixButtonSize.Lg, fullWidth = true)
            },
        ) {
            (problem ?: failure?.let { problemText(it) })?.let {
                QeetrixAlert(title = stringResource(R.string.admin_problem_title), description = it, tone = QeetrixAlertTone.Danger)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            AuditNote(stringResource(R.string.admin_suspended_audit))
        }
        return
    }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.admin_suspend), onBack = { navController.popBackStack() }) },
        actions = {
            if (member != null) {
                QeetrixButton(
                    stringResource(R.string.admin_suspend_action, firstName(member.name)),
                    onClick = {
                        val chosen = reason ?: return@QeetrixButton
                        busy = true
                        problem = null
                        failure = null
                        scope.launch {
                            // Always the phone's own check first, even with the app open and unlocked.
                            when (
                                verification.verify(
                                    VerifyPrompt(
                                        container.context.getString(R.string.admin_suspend_confirm, member.name),
                                        reasonLabels.getValue(chosen),
                                    ),
                                )
                            ) {
                                VerifyResult.Verified -> {
                                    when (val result = admin.inOrganization { access, _ -> admin.api.suspend(access, member.id) }) {
                                        is AdminResult.Ok -> {
                                            admin.inOrganization { access, _ -> admin.api.recordSuspension(access, member.id, chosen) }
                                            suspended = true
                                        }

                                        AdminResult.SignedOut -> {
                                            navController.toAdminSignIn()
                                        }

                                        else -> {
                                            failure = result
                                        }
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
                    enabled = reason != null && !busy,
                    loading = busy,
                    fullWidth = true,
                )
            }
        },
    ) {
        AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { person ->
            if (reactivated) {
                QeetrixAlert(title = stringResource(R.string.admin_reactivated, person.name), tone = QeetrixAlertTone.Success)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            (problem ?: failure?.let { problemText(it) })?.let {
                QeetrixAlert(title = stringResource(R.string.admin_problem_title), description = it, tone = QeetrixAlertTone.Danger)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            PersonHeader(
                stringResource(R.string.admin_suspend_question, person.name),
                listOfNotNull(person.email.takeIf { it != person.name }, person.team).joinToString(" · ").ifEmpty { null },
            )
            Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
            Panel {
                StopFact(stringResource(R.string.admin_suspend_out))
                StopFact(stringResource(R.string.admin_suspend_cannot))
                Fact(stringResource(R.string.admin_suspend_stays))
            }
            Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
            Box {
                QeetrixRadioGroup(
                    value = reason,
                    onValueChange = { reason = it },
                    options = SuspendReason.entries.map { QeetrixOption<SuspendReason?>(it, reasonLabels.getValue(it)) },
                    label = stringResource(R.string.admin_suspend_reason),
                    description =
                        stringResource(
                            R.string.admin_suspend_reason_help,
                            com.qeetgroup.qeetid.ui
                                .deviceWord(),
                        ),
                )
            }
        }
    }
}

@Composable
private fun suspendReasonLabels(): Map<SuspendReason, String> =
    mapOf(
        SuspendReason.SuspiciousActivity to stringResource(R.string.admin_reason_suspicious),
        SuspendReason.AccountCompromise to stringResource(R.string.admin_reason_compromise),
        SuspendReason.PolicyViolation to stringResource(R.string.admin_reason_policy),
        SuspendReason.Offboarding to stringResource(R.string.admin_reason_leaving),
        SuspendReason.Other to stringResource(R.string.admin_reason_other),
    )
