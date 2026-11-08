package com.qeetgroup.qeetid.approvals

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.push.SignInNotifications
import com.qeetgroup.qeetid.data.api.SignInRequest
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.LinkPhone
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.ResultDetails
import com.qeetgroup.qeetid.ui.ResultNote
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.Settings
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.components.otpinput.QeetrixOTPInput
import com.qeetgroup.qeetrix.components.otpinput.QeetrixOTPSlotWidth
import com.qeetgroup.qeetrix.components.spinner.QeetrixSpinner
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Where "Change password" goes: the account's Security page in Qeet ID, on the web. */
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

/** Review the request, Confirm it's you (the system's prompt), and how it ended: Approved, Denied, Request ended. */
@Composable
fun RequestScreen(
    state: RequestState,
    model: RequestViewModel,
    onClose: () -> Unit,
    onLink: () -> Unit,
    onSettings: () -> Unit,
) {
    val haptics = rememberQeetrixHaptics()
    LaunchedEffect(state::class) {
        when (state) {
            is RequestState.Approved -> haptics.perform(QeetrixHapticRole.Success)
            is RequestState.Denied -> haptics.perform(QeetrixHapticRole.Warning)
            is RequestState.Ended -> if (state.reason == EndReason.NumberMismatch) haptics.perform(QeetrixHapticRole.Error)
            else -> Unit
        }
    }

    when (state) {
        RequestState.Loading -> {
            Screen(topBar = { ScreenBar(stringResource(R.string.request_title), onBack = onClose) }) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QeetrixSpinner(label = stringResource(R.string.request_title))
                }
            }
        }

        is RequestState.Review -> {
            ReviewPage(state, model, onClose)
        }

        is RequestState.Approved -> {
            ResultPage(
                title = stringResource(R.string.request_approved_title),
                body = stringResource(R.string.request_approved_body),
                sealTone = SealTone.Success,
                seal = { QeetrixIcon(QeetrixIconName.Check, null, Modifier.fillMaxSize()) },
                actions = { DoneButton(onClose) },
            ) {
                RequestDetails(state.request, withPlace = true)
                ResultNote(QeetrixIconName.ShieldAlert, stringResource(R.string.request_approved_undo))
            }
        }

        is RequestState.Denied -> {
            DeniedResult(
                title = stringResource(R.string.request_denied_title),
                onDone = onClose,
            ) { RequestDetails(state.request, withPlace = true) }
        }

        is RequestState.Ended -> {
            if (state.reason == EndReason.NumberMismatch && state.request != null) {
                WrongNumberPage(state.request, onClose)
            } else {
                ResultPage(
                    title = stringResource(R.string.request_ended_title),
                    body =
                        stringResource(
                            if (state.reason ==
                                EndReason.Gone
                            ) {
                                R.string.request_ended_gone
                            } else {
                                R.string.request_ended_expired
                            },
                        ),
                    sealTone = SealTone.Brand,
                    seal = { QeetrixIcon(QeetrixIconName.Clock, null, Modifier.fillMaxSize()) },
                    actions = { DoneButton(onClose) },
                ) {
                    state.request?.let { RequestDetails(it, withPlace = false) }
                    ResultNote(QeetrixIconName.RotateCcw, stringResource(R.string.request_ended_again))
                }
            }
        }

        is RequestState.Answered -> {
            ResultPage(
                title = stringResource(R.string.request_answered_title),
                body = stringResource(R.string.request_answered_body),
                sealTone = SealTone.Brand,
                seal = { QeetrixIcon(QeetrixIconName.Smartphone, null, Modifier.fillMaxSize()) },
                actions = { DoneButton(onClose) },
            ) { RequestDetails(state.request, withPlace = false) }
        }

        RequestState.LoadFailed -> {
            ResultPage(
                title = stringResource(R.string.request_load_error_title),
                body = stringResource(R.string.request_error_network),
                sealTone = SealTone.Brand,
                seal = { QeetrixIcon(QeetrixIconName.CircleAlert, null, Modifier.fillMaxSize()) },
                actions = {
                    QuietButton(stringResource(R.string.action_done), onClose)
                    QeetrixButton(
                        stringResource(R.string.action_try_again),
                        onClick = model::load,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                },
            )
        }

        RequestState.NotLinked -> {
            val device = deviceWord()
            ResultPage(
                title = stringResource(R.string.request_not_linked_title, device),
                body = stringResource(R.string.request_not_linked_body),
                sealTone = SealTone.Brand,
                seal = { QeetrixIcon(QeetrixIconName.Lock, null, Modifier.fillMaxSize()) },
                actions = {
                    QuietButton(stringResource(R.string.action_done), onClose)
                    QeetrixButton(
                        stringResource(R.string.link_phone, device),
                        onClick = onLink,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                },
            )
        }

        RequestState.NotThisAccount -> {
            val device = deviceWord()
            ResultPage(
                title = stringResource(R.string.request_not_this_account_title, device),
                body = stringResource(R.string.request_not_this_account_body),
                sealTone = SealTone.Danger,
                seal = { QeetrixIcon(QeetrixIconName.X, null, Modifier.fillMaxSize()) },
                actions = {
                    QuietButton(stringResource(R.string.action_done), onClose)
                    QeetrixButton(
                        stringResource(R.string.settings_title),
                        onClick = onSettings,
                        size = QeetrixButtonSize.Lg,
                        fullWidth = true,
                    )
                },
            )
        }
    }
}

/**
 * Review the request: is this you signing in, to which account, what's signing in from where and when, and the number
 * from the sign-in screen. Approve waits for both digits, then asks for the fingerprint or screen lock (Confirm it's
 * you); Deny is always beside it. The time left counts down in the bar.
 */
@Composable
private fun ReviewPage(
    state: RequestState.Review,
    model: RequestViewModel,
    onClose: () -> Unit,
) {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val scope = rememberCoroutineScope()
    val link by container.links.summary.collectAsStateWithLifecycle()
    val number = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val short = isShort()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val facts = requestFacts(state.request)
    val verifyTitle = stringResource(R.string.request_verify_title)
    // The reason under the prompt's title names the sign-in: "Chrome on macOS, near Bengaluru".
    val verifySubtitle =
        when {
            facts.browser != null && facts.place != null -> stringResource(R.string.request_verify_reason, facts.browser, facts.place)
            facts.browser != null -> facts.browser
            else -> stringResource(R.string.request_verify_subtitle)
        }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ScrollingPage(
        topBar = {
            ScreenBar(
                stringResource(R.string.request_title),
                onBack = onClose,
                description = stringResource(R.string.request_time_left, clock(state.secondsLeft)),
            )
        },
        actions = {
            QeetrixButton(
                stringResource(R.string.request_deny),
                onClick = model::deny,
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
                size = QeetrixButtonSize.Lg,
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
                enabled = number.text.length == NUMBER_LENGTH && !state.sending,
                loading = state.sending,
                fullWidth = true,
            )
        },
    ) {
        state.error?.let {
            QeetrixAlert(title = stringResource(it), tone = QeetrixAlertTone.Danger)
            Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
        }
        RequestHeading(state.request, link?.accountEmail)
        Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else 20.dp))
        FactsCard(facts)
        Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = stringResource(R.string.request_number_label)
            QeetrixText(stringResource(R.string.request_number_title), weight = QeetrixTextWeight.Semibold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(QeetrixSpace.GapMd))
            QeetrixOTPInput(
                number,
                label = label,
                length = NUMBER_LENGTH,
                hideLabel = true,
                smsCodeAutofill = false,
                slotWidth = QeetrixOTPSlotWidth.Square,
                enabled = !state.sending,
                modifier =
                    Modifier
                        // Two square slots, centred, wider as the text grows.
                        .width((QeetrixTheme.density.sizeControlLg * NUMBER_LENGTH + QeetrixSpace.GapSm) * fontScale)
                        .focusRequester(focus)
                        .testTag("request-number"),
            )
            Spacer(Modifier.height(QeetrixSpace.GapMd))
            QeetrixText(
                stringResource(R.string.request_number_help),
                size = QeetrixTextSize.Sm,
                tone = QeetrixTextTone.Subtle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The number didn't match: Qeet ID stopped the sign-in. The request stays on screen, so people see which one. */
@Composable
private fun WrongNumberPage(
    request: SignInRequest,
    onClose: () -> Unit,
) {
    val container = LocalAppContainer.current
    val link by container.links.summary.collectAsStateWithLifecycle()
    val short = isShort()
    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.request_title), onBack = onClose) },
        actions = { DoneButton(onClose) },
    ) {
        QeetrixAlert(
            title = stringResource(R.string.request_wrong_title),
            description = stringResource(R.string.request_wrong_body),
            tone = QeetrixAlertTone.Danger,
        )
        Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
        RequestHeading(request, link?.accountEmail)
        Spacer(Modifier.height(if (short) QeetrixSpace.GapMd else 20.dp))
        FactsCard(requestFacts(request))
    }
}

/** "Is this you signing in?" and, under it, the account it's for. */
@Composable
private fun RequestHeading(
    request: SignInRequest,
    email: String?,
) {
    val short = isShort()
    QeetrixHeading(question(request.action), level = 1, size = if (short) QeetrixHeadingSize.HeadingSm else QeetrixHeadingSize.HeadingMd)
    if (!email.isNullOrBlank()) {
        Spacer(Modifier.height(QeetrixSpace.GapXs))
        QeetrixText(stringResource(R.string.request_to, email), tone = QeetrixTextTone.Subtle)
    }
}

/** What's signing in, where and when, as the request tells it; each only when the server knows it. */
private data class RequestFacts(
    val browser: String?,
    val place: String?,
    val day: String?,
    val time: String?,
    val ip: String?,
)

@Composable
private fun requestFacts(request: SignInRequest): RequestFacts {
    val context = LocalContext.current
    val at = RequestViewModel.requestedAtMillis(request)
    val time = at?.let { QeetrixFormat.time(Instant.ofEpochMilli(it), hour24 = DateFormat.is24HourFormat(context)) }
    val today = stringResource(R.string.request_today_at, time.orEmpty())
    return RequestFacts(
        browser = browserOf(request),
        place = request.context.location?.takeIf { it.isNotBlank() },
        day = at?.let { if (DateUtils.isToday(it)) today else dayAndTime(context, it) },
        time = time,
        ip = request.context.ip?.takeIf { it.isNotBlank() },
    )
}

private fun dayAndTime(
    context: Context,
    millis: Long,
): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

/** The sign-in's facts on ruled rows, each with its icon: laptop, pin, clock, globe. */
@Composable
private fun FactsCard(facts: RequestFacts) {
    val colors = QeetrixTheme.colors
    val short = isShort()
    val rows =
        listOfNotNull(
            facts.browser?.let { QeetrixIconName.Laptop to it },
            facts.place?.let { QeetrixIconName.MapPin to stringResource(R.string.request_near_place, it) },
            facts.day?.let { QeetrixIconName.Clock to it },
            facts.ip?.let { QeetrixIconName.Globe to stringResource(R.string.request_ip_value, it) },
        )
    if (rows.isEmpty()) return
    val label = stringResource(R.string.request_details)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .background(colors.surfaceRaised, QeetrixShapes.Surface)
            .border(1.dp, colors.borderDefault, QeetrixShapes.Surface),
    ) {
        rows.forEachIndexed { index, (icon, text) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = QeetrixSpace.InsetLg, vertical = if (short) QeetrixSpace.InsetSm else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QeetrixIcon(icon, null, size = QeetrixSize.IconSm, tint = colors.iconSubtle)
                QeetrixText(text)
            }
        }
    }
}

/** Browser, where and time, label and value, for a result; where only when [withPlace]. */
@Composable
private fun RequestDetails(
    request: SignInRequest,
    withPlace: Boolean,
) {
    val facts = requestFacts(request)
    val rows =
        listOfNotNull(
            facts.browser?.let { stringResource(R.string.request_browser) to it },
            facts.place?.takeIf { withPlace }?.let {
                stringResource(R.string.request_where) to
                    stringResource(R.string.request_near_place, it)
            },
            facts.time?.let { stringResource(R.string.request_time) to it },
        )
    if (rows.isEmpty()) return
    ResultDetails(rows)
    Spacer(Modifier.height(QeetrixSpace.GapLg))
}

/**
 * Denied, for one request or for all of them: the icon with a red seal, nobody signed in, and the next step spelt
 * out, since whoever tried got past the first step (usually the password). Change password opens the Security page.
 */
@Composable
internal fun DeniedResult(
    title: String,
    onDone: () -> Unit,
    details: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    ResultPage(
        title = title,
        body = stringResource(R.string.request_denied_body),
        sealTone = SealTone.Danger,
        seal = { QeetrixIcon(QeetrixIconName.X, null, Modifier.fillMaxSize()) },
        actions = {
            QuietButton(stringResource(R.string.action_done), onDone)
            QeetrixButton(
                stringResource(R.string.request_change_password),
                onClick = { openSecurityPage(context) },
                size = QeetrixButtonSize.Lg,
                fullWidth = true,
            )
        },
    ) {
        details()
        ResultNote(QeetrixIconName.KeyRound, stringResource(R.string.request_denied_next), warning = true)
    }
}

private fun openSecurityPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, SECURITY_PAGE.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing to open.
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    QeetrixButton(stringResource(R.string.action_done), onClick = onClick, size = QeetrixButtonSize.Lg, fullWidth = true)
}

/** The quiet action beside the main one (Done, Not now). */
@Composable
internal fun QuietButton(
    text: String,
    onClick: () -> Unit,
) {
    QeetrixButton(
        text,
        onClick = onClick,
        variant = QeetrixButtonVariant.Ghost,
        tone = QeetrixButtonTone.Neutral,
        size = QeetrixButtonSize.Lg,
        fullWidth = true,
    )
}

@Composable
internal fun browserOf(request: SignInRequest): String? {
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
internal fun clock(seconds: Int): String = "%d:%02d".format(Locale.getDefault(), seconds / 60, seconds % 60)
