package com.qeetgroup.qeetid.admin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.spinner.QeetrixSpinner
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A screen's actions: they resume on the main thread whatever thread answered, and outlive a configuration change. */
internal class Actions : ViewModel() {
    val scope: CoroutineScope get() = viewModelScope
}

/** The scope a screen runs its taps' work in (sign in, approve, suspend), instead of the composition's. */
@Composable
internal fun rememberActions(): CoroutineScope = viewModel<Actions>().scope

/** Loads a screen's data from Qeet ID, and again on Try again or on coming back to the screen. */
internal class Loader<T>(
    private val load: suspend () -> AdminResult<T>,
) : ViewModel() {
    private val _state = MutableStateFlow<AdminResult<T>?>(null)

    /** Null while loading the first time. */
    val state: StateFlow<AdminResult<T>?> = _state.asStateFlow()

    init {
        reload()
    }

    /** Loads again; [quietly] keeps what's shown until the answer comes. */
    fun reload(quietly: Boolean = false) {
        if (!quietly) _state.value = null
        viewModelScope.launch {
            val next = load()
            // A quiet reload that fails keeps what was shown.
            if (!quietly || next is AdminResult.Ok || _state.value !is AdminResult.Ok) _state.value = next
        }
    }
}

/**
 * A screen's body for [result]: a spinner while it loads, [content] once it's there, the problem with Try again when
 * it isn't, and [onSignedOut] when the session has ended.
 */
@Composable
internal fun <T> AdminContent(
    result: AdminResult<T>?,
    onRetry: () -> Unit,
    onSignedOut: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (result) {
        null -> {
            Box(Modifier.fillMaxWidth().padding(QeetrixSpace.LayoutMd), contentAlignment = Alignment.Center) {
                QeetrixSpinner(label = stringResource(R.string.admin_loading))
            }
        }

        is AdminResult.Ok -> {
            content(result.value)
        }

        AdminResult.SignedOut -> {
            LaunchedEffect(Unit) { onSignedOut() }
        }

        else -> {
            Box(Modifier.readingColumn().padding(QeetrixSpace.InsetLg)) {
                QeetrixAlert(
                    title = stringResource(R.string.admin_problem_title),
                    description = problemText(result),
                    tone = QeetrixAlertTone.Danger,
                    actions = { QeetrixButton(stringResource(R.string.action_try_again), onClick = onRetry, size = QeetrixButtonSize.Sm) },
                )
            }
        }
    }
}

/** Why a call didn't go through, in words. */
@Composable
internal fun problemText(result: AdminResult<*>): String =
    stringResource(
        when (result) {
            AdminResult.Network -> R.string.admin_problem_network
            AdminResult.Forbidden -> R.string.admin_problem_forbidden
            AdminResult.NotFound -> R.string.admin_problem_gone
            is AdminResult.Conflict -> R.string.admin_problem_changed
            AdminResult.StepUpRequired -> R.string.admin_problem_step_up
            else -> R.string.admin_problem_refused
        },
    )

/** A scrolling column of settings-style groups, under the bar, as the administration lists are laid out. */
@Composable
internal fun ColumnScope.GroupsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = 20.dp),
        content = content,
    )
}

/** A person's or organisation's initials on a 4 dp tile; brand-tinted for an organisation. */
@Composable
internal fun Initials(
    name: String,
    size: Dp = 40.dp,
    brand: Boolean = false,
) {
    val colors = QeetrixTheme.colors
    Box(
        Modifier
            .size(size)
            .background(if (brand) colors.bgBrandSubtle else colors.bgNeutralSubtle, QeetrixShapes.Control)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        QeetrixText(
            initialsOf(name),
            weight = QeetrixTextWeight.Semibold,
            tone = if (brand) QeetrixTextTone.Brand else null,
            maxLines = 1,
        )
    }
}

/** "AR" for Arjun Rao, "A" for arjun@…. */
internal fun initialsOf(name: String): String {
    val words = name.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return words
        .take(2)
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
}

/** The first word of a name, for "Suspend Kabir". */
internal fun firstName(name: String): String =
    name
        .substringBefore('@')
        .trim()
        .substringBefore(' ')
        .ifEmpty { name }

/** A label and its value on a ruled row, the label on a fixed column (the request and event details). */
@Composable
internal fun FactRows(rows: List<Pair<String, String>>) {
    val colors = QeetrixTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, QeetrixShapes.Surface)
            .border(1.dp, colors.borderDefault, QeetrixShapes.Surface),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = QeetrixSpace.InsetLg, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
            ) {
                QeetrixText(
                    label,
                    modifier = Modifier.width(72.dp).padding(top = 2.dp),
                    size = QeetrixTextSize.Sm,
                    tone = QeetrixTextTone.Subtle,
                )
                QeetrixText(value, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** When something happened, relative: "2 h ago". */
internal fun ago(
    iso: String,
    now: Long,
): String? =
    millis(iso)?.let {
        DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }

internal fun millis(iso: String?): Long? =
    try {
        iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

/** A day, as "15 November". */
internal fun day(
    context: Context,
    millis: Long,
): String = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)

/** How long the access lasts: "For 8 hours", "For 7 days", "Until it's taken away". */
@Composable
internal fun durationText(hours: Int?): String =
    when {
        hours == null -> stringResource(R.string.admin_access_open_ended)
        hours < 24 || hours % 24 != 0 -> pluralStringResource(R.plurals.admin_access_hours, hours, hours)
        else -> pluralStringResource(R.plurals.admin_access_days, hours / 24, hours / 24)
    }

/** The console, beside the API it talks to: api.id.qeet.in → console.id.qeet.in. */
internal fun consoleBase(api: HttpUrl): String {
    val host = api.host
    return if (host.startsWith("api.")) "${api.scheme}://console.${host.removePrefix("api.")}" else "https://console.id.qeet.in"
}

/** Opens [uri] outside the app; nothing when nothing can open it. */
internal fun openOutside(
    context: Context,
    uri: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser.
    }
}
