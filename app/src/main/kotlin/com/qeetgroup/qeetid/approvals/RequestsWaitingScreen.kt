package com.qeetgroup.qeetid.approvals

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.approvals.push.SignInNotifications
import com.qeetgroup.qeetid.data.api.PendingResult
import com.qeetgroup.qeetid.data.api.RespondResult
import com.qeetgroup.qeetid.data.api.SignInRequest
import com.qeetgroup.qeetid.ui.Accounts
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.SignInRequestRoute
import com.qeetgroup.qeetid.ui.isTablet
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.list.QeetrixListItem
import com.qeetgroup.qeetrix.components.spinner.QeetrixSpinner
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Date

/** Three or more at once: the alert turns red and says not to approve any. */
private const val MANY = 3

/** A waiting request and when it stops being answerable, by this phone's clock corrected by the server's. */
data class WaitingRequest(
    val request: SignInRequest,
    val expiresAtLocal: Long,
    val secondsLeft: Int,
)

sealed interface WaitingState {
    data object Loading : WaitingState

    data object LoadFailed : WaitingState

    /** Nothing waiting any more (answered, ended, or the phone is no longer linked). */
    data object Empty : WaitingState

    data class Waiting(
        val requests: List<WaitingRequest>,
        val denying: Boolean = false,
        /** A string resource saying why Deny all didn't finish. */
        val error: Int? = null,
    ) : WaitingState

    /** Deny all went through, for [ids]. */
    data class DeniedAll(
        val ids: List<String>,
    ) : WaitingState
}

/** Requests waiting: the server's list for this phone, each one's time left, and Deny all. */
class RequestsWaitingViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<WaitingState>(WaitingState.Loading)
    val state: StateFlow<WaitingState> = _state.asStateFlow()
    private var countdown: Job? = null

    init {
        load()
    }

    fun load(quietly: Boolean = false) {
        if (_state.value is WaitingState.DeniedAll) return
        if (!quietly) _state.value = WaitingState.Loading
        viewModelScope.launch { _state.value = fetch(quietly) }
    }

    private suspend fun fetch(quietly: Boolean): WaitingState {
        val link = container.links.get() ?: return WaitingState.Empty
        return when (val result = container.api.pendingRequests(link.deviceId, link.userId, link.deviceToken)) {
            is PendingResult.Found -> {
                val requests =
                    result.requests
                        .filter { it.status == SignInRequest.PENDING && RequestInbox.isRequestId(it.id) }
                        .mapNotNull { request ->
                            parseTime(request.expiresAt)?.let { WaitingRequest(request, it - result.clockOffsetMillis, 0) }
                        }
                tick(requests).also { if (it is WaitingState.Waiting) startCountdown() }
            }

            PendingResult.Unauthorized -> {
                WaitingState.Empty
            }

            PendingResult.NotSupported, PendingResult.Network -> {
                _state.value.takeIf { quietly && it is WaitingState.Waiting } ?: WaitingState.LoadFailed
            }
        }
    }

    /** Denies every waiting request; a request that ended meanwhile needs no answer. */
    fun denyAll() {
        val waiting = _state.value as? WaitingState.Waiting ?: return
        if (waiting.denying) return
        _state.value = waiting.copy(denying = true, error = null)
        viewModelScope.launch {
            val link = container.links.get()
            if (link == null) {
                _state.value = WaitingState.Empty
                return@launch
            }
            val failed =
                waiting.requests.count { item ->
                    when (container.api.respond(item.request.id, link.userId, link.deviceToken, approved = false, number = null)) {
                        RespondResult.Network, is RespondResult.Refused -> true
                        else -> false
                    }
                }
            countdown?.cancel()
            _state.value =
                if (failed == 0) {
                    WaitingState.DeniedAll(waiting.requests.map { it.request.id })
                } else {
                    // The list again, without the ones denied, and why it isn't empty.
                    when (val next = fetch(quietly = false)) {
                        is WaitingState.Waiting -> next.copy(error = R.string.requests_deny_failed)
                        else -> next
                    }
                }
        }
    }

    private fun tick(requests: List<WaitingRequest>): WaitingState {
        val now = container.now()
        val left =
            requests
                .map { it.copy(secondsLeft = ((it.expiresAtLocal - now + 999) / 1000).coerceAtLeast(0).toInt()) }
                .filter { it.secondsLeft > 0 }
        return if (left.isEmpty()) WaitingState.Empty else WaitingState.Waiting(left)
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MILLIS - container.now() % TICK_MILLIS)
                    _state.update { current ->
                        if (current !is WaitingState.Waiting || current.denying) {
                            current
                        } else {
                            when (val next = tick(current.requests)) {
                                is WaitingState.Waiting -> current.copy(requests = next.requests)
                                else -> next
                            }
                        }
                    }
                    if (_state.value !is WaitingState.Waiting) break
                }
            }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L

        fun parseTime(text: String): Long? =
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
    }
}

@Composable
fun RequestsWaitingRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val model = viewModel { RequestsWaitingViewModel(container) }
    val state by model.state.collectAsStateWithLifecycle()
    val close: () -> Unit = { if (!navController.popBackStack()) navController.navigate(Accounts) }
    var shown by remember { mutableStateOf(false) }
    // Back from reviewing one: the list again, without the ones answered.
    LifecycleResumeEffect(Unit) {
        if (shown) model.load(quietly = true)
        shown = true
        onPauseOrDispose {}
    }
    LaunchedEffect(state) { if (state == WaitingState.Empty) close() }
    when (val now = state) {
        is WaitingState.DeniedAll -> {
            LaunchedEffect(now) { now.ids.forEach { SignInNotifications.cancel(context, it) } }
            DeniedResult(title = stringResource(R.string.requests_denied_title), onDone = close)
        }

        else -> {
            RequestsWaitingScreen(
                state = now,
                onBack = close,
                onOpen = { navController.navigate(SignInRequestRoute(it)) },
                onDenyAll = model::denyAll,
                onRetry = { model.load() },
            )
        }
    }
}

/**
 * More than one sign-in waiting for this phone: an alert that says what it can mean (red when there are lots), each
 * request's what, where, when and time left, and Deny all as the main action.
 */
@Composable
fun RequestsWaitingScreen(
    state: WaitingState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDenyAll: () -> Unit,
    onRetry: () -> Unit,
) {
    val waiting = state as? WaitingState.Waiting
    val scroll = rememberScrollState()
    val tablet = isTablet()
    val colors = QeetrixTheme.colors
    Screen(
        topBar = {
            ScreenBar(
                stringResource(R.string.requests_title),
                onBack = onBack,
                description = waiting?.let { pluralStringResource(R.plurals.requests_waiting, it.requests.size, it.requests.size) },
            )
        },
        bottomBar = {
            when {
                waiting != null -> {
                    QeetrixBottomActionBar(scroll = scroll) {
                        QeetrixButton(
                            stringResource(R.string.requests_deny_all),
                            onClick = onDenyAll,
                            modifier = Modifier.readingColumn(),
                            tone = QeetrixButtonTone.Danger,
                            size = QeetrixButtonSize.Lg,
                            loading = waiting.denying,
                            fullWidth = true,
                        )
                    }
                }

                state == WaitingState.LoadFailed -> {
                    QeetrixBottomActionBar {
                        QuietButton(stringResource(R.string.action_done), onBack)
                        QeetrixButton(
                            stringResource(R.string.action_try_again),
                            onClick = onRetry,
                            size = QeetrixButtonSize.Lg,
                            fullWidth = true,
                        )
                    }
                }
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            when {
                state == WaitingState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(QeetrixSpace.LayoutMd), contentAlignment = Alignment.Center) {
                        QeetrixSpinner(label = stringResource(R.string.requests_title))
                    }
                }

                state == WaitingState.LoadFailed -> {
                    Box(Modifier.readingColumn().padding(QeetrixSpace.InsetLg)) {
                        QeetrixAlert(title = stringResource(R.string.requests_load_failed), tone = QeetrixAlertTone.Danger)
                    }
                }

                waiting != null -> {
                    val count = waiting.requests.size
                    Column(
                        Modifier.readingColumn().padding(
                            start = QeetrixSpace.InsetLg,
                            end = QeetrixSpace.InsetLg,
                            top = QeetrixSpace.InsetLg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
                    ) {
                        waiting.error?.let { QeetrixAlert(title = stringResource(it), tone = QeetrixAlertTone.Danger) }
                        if (count >= MANY) {
                            QeetrixAlert(
                                title = stringResource(R.string.requests_many_title),
                                description = stringResource(R.string.requests_many_body),
                                tone = QeetrixAlertTone.Danger,
                            )
                        } else if (count > 1) {
                            QeetrixAlert(
                                title = stringResource(R.string.requests_two_title),
                                description = stringResource(R.string.requests_two_body),
                                tone = QeetrixAlertTone.Warning,
                            )
                        }
                    }
                    Spacer(Modifier.height(QeetrixSpace.InsetLg))
                    val label = stringResource(R.string.requests_list)
                    val list =
                        if (tablet) {
                            Modifier
                                .readingColumn()
                                .padding(horizontal = QeetrixSpace.InsetLg)
                                .background(colors.surfaceRaised, QeetrixShapes.Surface)
                                .border(1.dp, colors.borderDefault, QeetrixShapes.Surface)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceRaised)
                                .border(1.dp, colors.borderDefault)
                        }
                    Column(list.semantics { contentDescription = label }) {
                        waiting.requests.forEachIndexed { index, item ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                            WaitingRow(item, onClick = { onOpen(item.request.id) })
                        }
                    }
                    Spacer(Modifier.height(QeetrixSpace.InsetLg))
                }
            }
        }
    }
}

/** One waiting request: what's signing in, from where and when, and its time left. */
@Composable
private fun WaitingRow(
    item: WaitingRequest,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = QeetrixTheme.colors
    val request = item.request
    val what = browserOf(request) ?: stringResource(R.string.requests_unknown_browser)
    val time = RequestViewModel.requestedAtMillis(request)?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    val place =
        request.context.location
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.request_near_place, it) }
    val where =
        when {
            place != null && time != null -> stringResource(R.string.requests_where_when, place, time)
            else -> place ?: time
        }
    QeetrixListItem(
        title = what,
        description = where,
        onClick = onClick,
        leading = {
            Box(Modifier.size(40.dp).background(colors.bgNeutralSubtle, QeetrixShapes.Control), contentAlignment = Alignment.Center) {
                AppIcon(deviceIcon(request), null, size = QeetrixSize.IconMd, tint = colors.iconSubtle)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapXs)) {
                QeetrixText(
                    stringResource(R.string.request_time_left, clock(item.secondsLeft)),
                    size = QeetrixTextSize.Sm,
                    tone = QeetrixTextTone.Subtle,
                )
                QeetrixIcon(QeetrixIconName.ChevronRight, null, size = QeetrixSize.IconSm, tint = colors.iconSubtle)
            }
        },
    )
}

/** A laptop for a Mac or Chromebook, a phone for a phone or tablet, a monitor otherwise. */
private fun deviceIcon(request: SignInRequest): AppIconName {
    val system = request.context.userAgent?.let { BrowserName.parts(it).system }
    return when (system) {
        "macOS", "ChromeOS" -> AppIconName.Laptop
        "iOS", "iPadOS", "Android" -> AppIconName.Smartphone
        else -> AppIconName.Monitor
    }
}
