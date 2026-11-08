package com.qeetgroup.qeetid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.admin.NoPasskeys
import com.qeetgroup.qeetid.admin.PasskeyPrompt
import com.qeetgroup.qeetid.lock.UserVerification
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.accessibility.QeetrixAccessibility
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixSize
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme

/** The app's objects, for the screens' view models. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

/** Android's fingerprint, face or screen-lock prompt. */
val LocalUserVerification = staticCompositionLocalOf<UserVerification> { error("No UserVerification") }

/** The phone's passkey prompt, for an administrator's sign-in and step-ups. */
val LocalPasskeys = staticCompositionLocalOf<PasskeyPrompt> { NoPasskeys }

/** A screen: the page colour, the bar on top, the scrolling content, the actions pinned at the bottom. */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(QeetrixTheme.colors.surfaceCanvas)) {
        topBar()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        bottomBar()
    }
}

/** Qeet ID's icon, the keyhole, at the size of a list's leading mark (see [BrandMark] for the hero sizes). */
@Composable
fun AppMark(
    modifier: Modifier = Modifier,
    size: Dp = APP_MARK_SIZE,
) {
    BrandMark(modifier, size = size)
}

private val APP_MARK_SIZE = 48.dp

/**
 * A square tile with a letter or a number, the size of [com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon]'s:
 * an account's initial, a step's number. Qeetrix has no text tile (logged as a Qeetrix finding).
 */
@Composable
fun LetterTile(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = TILE_SIZE,
) {
    Box(
        modifier
            .size(size)
            .background(QeetrixTheme.colors.bgNeutralSubtle, QeetrixShapes.Control)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        QeetrixText(text, weight = QeetrixTextWeight.Semibold, maxLines = 1)
    }
}

private val TILE_SIZE = 40.dp

/** The first letter of [name], for its tile. */
fun initialOf(name: String): String =
    name
        .trim()
        .firstOrNull { it.isLetterOrDigit() }
        ?.uppercase() ?: "?"

/**
 * An action row in a bottom sheet (whose body supplies the side padding): a 20 dp icon, the label in `body.md` and, if
 * it helps, one line under it; red when destructive. Qeetrix's menu items need a QeetrixMenu, so a sheet's actions are
 * drawn here (logged as a Qeetrix finding).
 */
@Composable
fun SheetAction(
    icon: QeetrixIconName,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    description: String? = null,
) {
    val colors = QeetrixTheme.colors
    SheetAction(
        leading = {
            QeetrixIcon(
                icon,
                contentDescription = null,
                size = QeetrixSize.IconMd,
                tint = if (danger) colors.textDanger else colors.iconSubtle,
            )
        },
        label = label,
        onClick = onClick,
        modifier = modifier,
        danger = danger,
        description = description,
    )
}

/** [SheetAction] with an icon Qeetrix for Android doesn't have yet ([AppIconName]). */
@Composable
fun SheetAction(
    icon: AppIconName,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    description: String? = null,
) {
    val colors = QeetrixTheme.colors
    SheetAction(
        leading = {
            AppIcon(icon, contentDescription = null, size = QeetrixSize.IconMd, tint = if (danger) colors.textDanger else colors.iconSubtle)
        },
        label = label,
        onClick = onClick,
        modifier = modifier,
        danger = danger,
        description = description,
    )
}

@Composable
private fun SheetAction(
    leading: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    danger: Boolean,
    description: String?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = QeetrixAccessibility.MinimumTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = QeetrixSpace.InsetMd),
        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column {
            QeetrixText(label, tone = if (danger) QeetrixTextTone.Danger else null)
            if (description != null) QeetrixText(description, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }
}

/** A centred column for a state that fills the screen (empty, done, error). */
@Composable
fun CentredState(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(QeetrixSpace.InsetLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
