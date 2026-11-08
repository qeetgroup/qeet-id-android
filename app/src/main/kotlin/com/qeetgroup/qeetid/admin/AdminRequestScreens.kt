package com.qeetgroup.qeetid.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.settings.SettingsGroup
import com.qeetgroup.qeetid.settings.SettingsRow
import com.qeetgroup.qeetid.settings.SettingsRule
import com.qeetgroup.qeetid.ui.AdminRequestRoute
import com.qeetgroup.qeetid.ui.GroupLabel
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalPasskeys
import com.qeetgroup.qeetid.ui.ResultNote
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.drawer.QeetrixBottomSheet
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.input.QeetrixTextField
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

/** Access requests: the ones waiting for an administrator, newest first, each with who, what, for how long and when. */
@Composable
fun AdminRequestsRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val loader =
        viewModel(key = "admin-requests") {
            Loader { admin.inOrganization { access, tenant -> admin.api.accessRequests(access, tenant) } }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (shown) loader.reload(quietly = true)
        shown = true
        onPauseOrDispose {}
    }
    val page = (state as? AdminResult.Ok)?.value

    Screen(
        topBar = {
            ScreenBar(
                stringResource(R.string.admin_requests),
                onBack = { navController.popBackStack() },
                description = page?.let { pluralStringResource(R.plurals.admin_waiting, it.items.size, it.items.size) },
            )
        },
    ) {
        GroupsColumn {
            AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { requests ->
                val waiting = requests.items.filter { it.status == AccessRequest.PENDING }.sortedByDescending { millis(it.createdAt) ?: 0L }
                if (waiting.isEmpty()) {
                    QeetrixEmptyState(
                        stringResource(R.string.admin_requests_none),
                        icon = QeetrixIconName.CircleCheck,
                        description = stringResource(R.string.admin_requests_none_body),
                    )
                } else {
                    SettingsGroup(stringResource(R.string.admin_requests_list)) {
                        waiting.forEachIndexed { index, request ->
                            if (index > 0) SettingsRule()
                            val name = request.requester.name?.takeIf { it.isNotBlank() } ?: request.requester.email
                            SettingsRow(
                                label = name,
                                detail = stringResource(R.string.admin_dot, request.role.name, durationText(request.durationHours)),
                                value = ago(request.createdAt, container.now()),
                                start = { Initials(name) },
                                onClick = { navController.navigate(AdminRequestRoute(request.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Where one access request is in its decision. */
private sealed interface Decision {
    data object Review : Decision

    data object Deny : Decision

    data class Approved(
        val request: AccessRequest,
    ) : Decision
}

/**
 * One access request in full (who, what it gives, for how long, why), then Approve after a fresh passkey check (the
 * phone asks for the fingerprint or face), or Deny with a reason they will see. Every decision is in the audit log,
 * like one made in the console.
 */
@Composable
fun AdminRequestScreenRoute(
    navController: NavHostController,
    id: String,
) {
    val container = LocalAppContainer.current
    val admin = container.admin
    val passkeys = LocalPasskeys.current
    val context = LocalContext.current
    val scope = rememberActions()
    val loader =
        viewModel(key = "admin-request-$id") {
            Loader { admin.inOrganization { access, tenant -> admin.api.accessRequest(access, tenant, id) } }
        }
    val state by loader.state.collectAsStateWithLifecycle()
    var decision by remember { mutableStateOf<Decision>(Decision.Review) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val reason = rememberTextFieldState()
    val short = isShort()

    val approved = decision as? Decision.Approved
    if (approved != null) {
        val request = approved.request
        val name = request.requester.name?.takeIf { it.isNotBlank() } ?: request.requester.email
        val ends = millis(request.accessEndsAt)
        ResultPage(
            title = stringResource(R.string.admin_access_given),
            body =
                if (ends != null) {
                    stringResource(R.string.admin_access_given_until, name, request.role.name, day(context, ends))
                } else {
                    stringResource(R.string.admin_access_given_body, name, request.role.name)
                },
            sealTone = SealTone.Success,
            seal = { QeetrixIcon(QeetrixIconName.Check, null, Modifier.fillMaxSize()) },
            actions = {
                QeetrixButton(stringResource(R.string.action_done), onClick = {
                    navController.popBackStack()
                }, size = QeetrixButtonSize.Lg, fullWidth = true)
            },
        ) { AuditNote() }
        return
    }

    val stepUpProblem = stringResource(R.string.admin_step_up_failed)
    var lastFailure by remember { mutableStateOf<AdminResult<*>?>(null) }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.admin_request_title), onBack = { navController.popBackStack() }) },
        actions = {
            val request = (state as? AdminResult.Ok)?.value
            if (request != null && request.status == AccessRequest.PENDING) {
                QeetrixButton(
                    stringResource(R.string.request_deny),
                    onClick = { decision = Decision.Deny },
                    variant = QeetrixButtonVariant.Ghost,
                    tone = QeetrixButtonTone.Neutral,
                    size = QeetrixButtonSize.Lg,
                    enabled = !busy,
                    fullWidth = true,
                )
                QeetrixButton(
                    stringResource(R.string.request_approve),
                    onClick = {
                        busy = true
                        problem = null
                        lastFailure = null
                        scope.launch {
                            // The passkey check is the phone asking first: the person's own fingerprint or face.
                            when (admin.stepUp(passkeys)) {
                                StepUpOutcome.Done -> {
                                    when (
                                        val result =
                                            admin.inOrganization {
                                                access,
                                                tenant,
                                                ->
                                                admin.api.approve(access, tenant, request.id, "")
                                            }
                                    ) {
                                        is AdminResult.Ok -> decision = Decision.Approved(result.value)
                                        AdminResult.SignedOut -> navController.toAdminSignIn()
                                        else -> lastFailure = result
                                    }
                                }

                                StepUpOutcome.Cancelled -> {}

                                StepUpOutcome.SignedOut -> {
                                    navController.toAdminSignIn()
                                }

                                else -> {
                                    problem = stepUpProblem
                                }
                            }
                            busy = false
                        }
                    },
                    size = QeetrixButtonSize.Lg,
                    loading = busy,
                    fullWidth = true,
                )
            } else if (request != null) {
                QeetrixButton(stringResource(R.string.action_done), onClick = {
                    navController.popBackStack()
                }, size = QeetrixButtonSize.Lg, fullWidth = true)
            }
        },
    ) {
        AdminContent(state, onRetry = { loader.reload() }, onSignedOut = { navController.toAdminSignIn() }) { request ->
            val name = request.requester.name?.takeIf { it.isNotBlank() } ?: request.requester.email
            (problem ?: lastFailure?.let { problemText(it) })?.let {
                QeetrixAlert(title = stringResource(R.string.admin_problem_title), description = it, tone = QeetrixAlertTone.Danger)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            if (request.status != AccessRequest.PENDING) {
                QeetrixAlert(title = stringResource(R.string.admin_request_decided), tone = QeetrixAlertTone.Info)
                Spacer(Modifier.height(QeetrixSpace.InsetLg))
            }
            PersonHeader(name, request.requester.email.takeIf { it != name })
            Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
            Box(
                Modifier.padding(bottom = QeetrixSpace.GapSm).semantics { heading() },
            ) { GroupLabel(stringResource(R.string.admin_asks_for)) }
            FactRows(
                listOfNotNull(
                    stringResource(R.string.admin_access) to request.role.name,
                    stringResource(R.string.admin_for) to durationText(request.durationHours),
                    request.role.description
                        ?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.admin_gives) to it },
                    ago(request.createdAt, container.now())?.let { stringResource(R.string.admin_asked) to it },
                ),
            )
            if (request.reason.isNotBlank()) {
                Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                Box(
                    Modifier.padding(bottom = QeetrixSpace.GapSm).semantics { heading() },
                ) { GroupLabel(stringResource(R.string.admin_their_reason)) }
                Quote(request.reason)
            }
        }
    }

    val request = (state as? AdminResult.Ok)?.value
    if (request != null) {
        val name = request.requester.name?.takeIf { it.isNotBlank() } ?: request.requester.email
        QeetrixBottomSheet(
            open = decision == Decision.Deny,
            onOpenChange = { if (!it) decision = Decision.Review },
            title = stringResource(R.string.admin_deny_title),
            description = stringResource(R.string.admin_deny_subtitle, firstName(name)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapLg)) {
                QeetrixTextField(reason, stringResource(R.string.admin_deny_reason))
                QeetrixButton(
                    stringResource(R.string.admin_deny_action),
                    onClick = {
                        busy = true
                        scope.launch {
                            when (
                                val result =
                                    admin.inOrganization {
                                        access,
                                        tenant,
                                        ->
                                        admin.api.deny(access, tenant, request.id, reason.text.toString().trim())
                                    }
                            ) {
                                is AdminResult.Ok -> {
                                    decision = Decision.Review
                                    navController.popBackStack()
                                }

                                AdminResult.SignedOut -> {
                                    navController.toAdminSignIn()
                                }

                                else -> {
                                    decision = Decision.Review
                                    lastFailure = result
                                }
                            }
                            busy = false
                        }
                    },
                    tone = QeetrixButtonTone.Danger,
                    size = QeetrixButtonSize.Lg,
                    enabled = reason.text.isNotBlank() && !busy,
                    loading = busy,
                    fullWidth = true,
                )
            }
        }
    }
}

/** A person's initials, name and a line under it (their email and team). */
@Composable
internal fun PersonHeader(
    title: String,
    line: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd), verticalAlignment = Alignment.CenterVertically) {
        Initials(title, size = 48.dp)
        Column(Modifier.weight(1f)) {
            QeetrixText(title, size = QeetrixTextSize.Lg, weight = QeetrixTextWeight.Semibold)
            line?.let { QeetrixText(it, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle) }
        }
    }
}

/** Someone's own words, quoted as they wrote them, on a brand rule. */
@Composable
private fun Quote(text: String) {
    val colors = QeetrixTheme.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.borderBrand))
        QeetrixText(text, modifier = Modifier.padding(start = QeetrixSpace.GapMd))
    }
}

/** "Recorded in the audit log, with this phone named." */
@Composable
internal fun AuditNote(text: String = stringResource(R.string.admin_audit_note, deviceWord())) {
    ResultNote(QeetrixIconName.FileText, text)
}
