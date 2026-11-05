package com.qeetgroup.qeetid.approvals

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.push.SignInNotifications
import com.qeetgroup.qeetid.codes.CloseButton
import com.qeetgroup.qeetid.data.api.SignInRequest
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.badge.QeetrixBadge
import com.qeetgroup.qeetrix.components.badge.QeetrixBadgeTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.card.QeetrixCard
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionItem
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionList
import com.qeetgroup.qeetrix.components.descriptionlist.QeetrixDescriptionListLayout
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyStateTone
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.otpinput.QeetrixOTPInput
import com.qeetgroup.qeetrix.components.spinner.QeetrixSpinner
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.haptics.QeetrixHapticRole
import com.qeetgroup.qeetrix.foundation.haptics.rememberQeetrixHaptics
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/** Where "Open security" goes: the account's security page in Qeet ID. */
private const val SECURITY_PAGE = "https://console.id.qeet.in/account/security"

/** The 2-digit number from the sign-in screen. */
private const val NUMBER_LENGTH = 2

@Composable
fun SignInRequestScreenRoute(
    navController: NavHostController,
    id: String,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val model = viewModel(key = "request-$id") { RequestViewModel(container, id) }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { SignInNotifications.cancel(context, id) }
    val close: () -> Unit = { if (!navController.popBackStack()) navController.navigate(Accounts) }
    RequestScreen(
        state = state,
        model = model,
        onClose = close,
        onLink = { navController.navigate(LinkPhone) { popUpTo(Accounts) } },
        onSettings = { navController.navigate(Settings) { popUpTo(Accounts) } },
    )
}

/** Screens 16–20: review, type the number, confirm it's you, and the outcome. */
@Composable
fun RequestScreen(
    state: RequestState,
    model: RequestViewModel,
    onClose: () -> Unit,
    onLink: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val verification = LocalUserVerification.current
    val scope = rememberCoroutineScope()
    val haptics = rememberQeetrixHaptics()
    val number = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val verifyTitle = stringResource(R.string.request_verify_title)
    val verifySubtitle = stringResource(R.string.request_verify_subtitle)

    LaunchedEffect(state::class) {
        when (state) {
            is RequestState.Approved -> haptics.perform(QeetrixHapticRole.Success)
            RequestState.Denied -> haptics.perform(QeetrixHapticRole.Warning)
            is RequestState.Ended -> if (state.reason == EndReason.NumberMismatch) haptics.perform(QeetrixHapticRole.Error)
            else -> Unit
        }
    }

    val review = state as? RequestState.Review
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Screen(
        topBar = {
            QeetrixTopAppBar(
                title = stringResource(R.string.request_title),
                navigationIcon = { CloseButton(onClose) },
                actions =
                    review?.let {
                        {
                            QeetrixBadge(
                                stringResource(R.string.request_time_left, clock(it.secondsLeft)),
                                modifier = Modifier.padding(end = QeetrixSpace.InsetSm),
                                tone = QeetrixBadgeTone.Warning,
                                icon = QeetrixIconName.Clock,
                            )
                        }
                    },
            )
        },
        bottomBar = {
            when (state) {
                is RequestState.Review -> {
                    QeetrixBottomActionBar(scroll = scroll) {
                        QeetrixButton(
                            stringResource(R.string.request_deny),
                            onClick = model::deny,
                            variant = QeetrixButtonVariant.Soft,
                            tone = QeetrixButtonTone.Neutral,
                            size = QeetrixButtonSize.Lg,
                            icon = QeetrixIconName.X,
                            enabled = !state.sending,
                            fullWidth = true,
                        )
                        QeetrixButton(
                            stringResource(R.string.request_approve),
                            onClick = {
                                val typed = number.text.toString().toIntOrNull() ?: return@QeetrixButton
                                scope.launch {
                                    when (verification.verify(VerifyPrompt(verifyTitle, verifySubtitle))) {
                                        VerifyResult.Verified -> model.approve(typed)
                                        VerifyResult.Unavailable -> model.notVerified(R.string.request_no_screen_lock)
                                        VerifyResult.Failed -> model.notVerified(R.string.request_verify_failed)
                                        VerifyResult.Cancelled -> Unit
                                    }
                                }
                            },
                            size = QeetrixButtonSize.Lg,
                            icon = QeetrixIconName.Check,
                            enabled = number.text.length == NUMBER_LENGTH && !state.sending,
                            loading = state.sending,
                            fullWidth = true,
                        )
                    }
                }

                RequestState.Loading -> {}

                RequestState.Denied -> {
                    QeetrixBottomActionBar {
                        QeetrixButton(
                            stringResource(R.string.action_done),
                            onClick = onClose,
                            variant = QeetrixButtonVariant.Ghost,
                            tone = QeetrixButtonTone.Neutral,
                            fullWidth = true,
                        )
                    }
                }

                else -> {
                    QeetrixBottomActionBar {
                        QeetrixButton(
                            stringResource(R.string.action_done),
                            onClick = onClose,
                            size = QeetrixButtonSize.Lg,
                            icon = QeetrixIconName.Check,
                            fullWidth = true,
                        )
                    }
                }
            }
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                RequestState.Loading -> {
                    QeetrixSpinner(Modifier.align(Alignment.Center), label = stringResource(R.string.request_title))
                }

                is RequestState.Review -> {
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(QeetrixSpace.InsetLg),
                        verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
                    ) {
                        QeetrixHeading(question(state.request.action), level = 1, size = QeetrixHeadingSize.TitleMd)
                        Details(state.request)
                        state.error?.let { QeetrixAlert(title = stringResource(it), tone = QeetrixAlertTone.Danger) }
                        Column(verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapXs)) {
                            val label = stringResource(R.string.request_number_label)
                            QeetrixText(label, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
                            QeetrixOTPInput(
                                number,
                                label = label,
                                length = NUMBER_LENGTH,
                                hideLabel = true,
                                smsCodeAutofill = false,
                                enabled = !state.sending,
                                modifier =
                                    Modifier
                                        // Two square slots, wider as the text grows.
                                        .width((QeetrixTheme.density.sizeControlLg * NUMBER_LENGTH + QeetrixSpace.GapSm) * fontScale)
                                        .focusRequester(focus)
                                        .testTag("request-number"),
                            )
                            QeetrixText(
                                stringResource(R.string.request_number_help),
                                size = QeetrixTextSize.Sm,
                                tone = QeetrixTextTone.Subtle,
                            )
                        }
                    }
                }

                is RequestState.Approved -> {
                    val browser = browserOf(state.request)
                    Outcome(
                        icon = QeetrixIconName.CircleCheck,
                        title = stringResource(R.string.request_approved_title),
                        body =
                            if (browser != null) {
                                stringResource(R.string.request_approved_body, browser)
                            } else {
                                stringResource(R.string.request_approved_body_generic)
                            },
                    )
                }

                RequestState.Denied -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        QeetrixEmptyState(
                            stringResource(R.string.request_denied_title),
                            icon = QeetrixIconName.CircleX,
                            description = stringResource(R.string.request_denied_body),
                            tone = QeetrixEmptyStateTone.Danger,
                            level = 1,
                        )
                        QeetrixAlert(
                            title = stringResource(R.string.request_denied_alert_title),
                            description = stringResource(R.string.request_denied_alert_body),
                            tone = QeetrixAlertTone.Warning,
                            modifier = Modifier.padding(horizontal = QeetrixSpace.InsetLg),
                            actions = {
                                QeetrixButton(
                                    stringResource(R.string.request_denied_alert_action),
                                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SECURITY_PAGE.toUri())) },
                                    size = QeetrixButtonSize.Sm,
                                )
                            },
                        )
                    }
                }

                is RequestState.Ended -> {
                    Outcome(
                        icon = QeetrixIconName.Clock,
                        title = stringResource(R.string.request_ended_title),
                        body =
                            stringResource(
                                when (state.reason) {
                                    EndReason.NumberMismatch -> R.string.request_ended_mismatch
                                    EndReason.Expired -> R.string.request_ended_expired
                                    EndReason.Gone -> R.string.request_ended_gone
                                },
                            ),
                        footnote = stringResource(R.string.request_ended_footnote),
                    )
                }

                is RequestState.Answered -> {
                    Outcome(
                        icon = QeetrixIconName.Info,
                        title = stringResource(R.string.request_ended_title),
                        body = stringResource(if (state.approved) R.string.request_answered_approved else R.string.request_answered_denied),
                    )
                }

                RequestState.LoadFailed -> {
                    Outcome(
                        icon = QeetrixIconName.CircleAlert,
                        title = stringResource(R.string.request_load_error_title),
                        body = stringResource(R.string.request_error_network),
                        action = { QeetrixButton(stringResource(R.string.action_try_again), onClick = model::load) },
                    )
                }

                RequestState.NotLinked -> {
                    Outcome(
                        icon = QeetrixIconName.Lock,
                        title = stringResource(R.string.request_not_linked_title),
                        body = stringResource(R.string.request_not_linked_body),
                        action = { QeetrixButton(stringResource(R.string.accounts_link_phone), onClick = onLink) },
                    )
                }

                RequestState.NotThisAccount -> {
                    Outcome(
                        icon = QeetrixIconName.CircleAlert,
                        title = stringResource(R.string.request_not_this_account_title),
                        body = stringResource(R.string.request_not_this_account_body),
                        action = {
                            QeetrixButton(
                                stringResource(R.string.link_go_settings),
                                onClick = onSettings,
                                variant = QeetrixButtonVariant.Ghost,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Outcome(
    icon: QeetrixIconName,
    title: String,
    body: String,
    footnote: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        QeetrixEmptyState(
            title,
            icon = icon,
            description = body,
            level = 1,
            actions = action,
            footer = footnote?.let { { QeetrixText(it, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle) } },
        )
    }
}

/** Where and when: browser, time, place and address — each only when the server knows it. */
@Composable
private fun Details(request: SignInRequest) {
    val context = LocalContext.current
    val browser = browserOf(request)
    val time = RequestViewModel.requestedAtMillis(request)?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    QeetrixCard(Modifier.fillMaxWidth()) {
        QeetrixDescriptionList(layout = QeetrixDescriptionListLayout.Columns, columns = 2) {
            browser?.let { QeetrixDescriptionItem(stringResource(R.string.request_browser), it) }
            time?.let { QeetrixDescriptionItem(stringResource(R.string.request_time), it) }
            request.context.location
                ?.takeIf { it.isNotBlank() }
                ?.let { QeetrixDescriptionItem(stringResource(R.string.request_near), it) }
            request.context.ip
                ?.takeIf { it.isNotBlank() }
                ?.let { QeetrixDescriptionItem(stringResource(R.string.request_ip), it) }
        }
    }
}

@Composable
private fun browserOf(request: SignInRequest): String? {
    val resources = LocalResources.current
    return BrowserName.describe(
        request.context.userAgent,
    ) { browser, system -> resources.getString(R.string.request_browser_on, browser, system) }
}

@Composable
private fun question(action: String): String =
    stringResource(
        when (action) {
            SignInRequest.ACTION_SIGN_IN -> R.string.request_question_sign_in
            ACTION_STEP_UP -> R.string.request_question_step_up
            else -> R.string.request_question_other
        },
    )

private const val ACTION_STEP_UP = "step_up"

/** "1:46": minutes and seconds left. */
private fun clock(seconds: Int): String = "%d:%02d".format(Locale.getDefault(), seconds / 60, seconds % 60)
