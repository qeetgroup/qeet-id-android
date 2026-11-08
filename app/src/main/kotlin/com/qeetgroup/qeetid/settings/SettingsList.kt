package com.qeetgroup.qeetid.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.GroupLabel
import com.qeetgroup.qeetid.ui.isTablet
import com.qeetgroup.qeetid.ui.readingColumn
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

/**
 * A titled group of rows, as Accounts: the title in caps, then the rows full width and ruled on a phone, or in a 4 dp
 * card in the reading column on a tablet.
 */
@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = QeetrixTheme.colors
    val tablet = isTablet()
    Column(
        Modifier
            .then(if (tablet) Modifier.readingColumn().padding(horizontal = QeetrixSpace.InsetLg) else Modifier.fillMaxWidth())
            .padding(bottom = QeetrixSpace.LayoutSm),
    ) {
        Box(Modifier.padding(start = if (tablet) 0.dp else QeetrixSpace.InsetLg, bottom = QeetrixSpace.GapSm).semantics { heading() }) {
            GroupLabel(title)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised, if (tablet) QeetrixShapes.Surface else QeetrixShapes.None)
                .border(1.dp, colors.borderDefault, if (tablet) QeetrixShapes.Surface else QeetrixShapes.None),
            content = content,
        )
    }
}

/** The rule between two rows of a [SettingsGroup]. */
@Composable
fun SettingsRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(QeetrixTheme.colors.borderSubtle))
}

/**
 * One setting: an optional mark at the start, the label and a line under it, and at the end a value, a control, or a
 * chevron when the row opens something (an arrow out when it leaves the app). A danger row is red, and last.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    danger: Boolean = false,
    external: Boolean = false,
    onClick: (() -> Unit)? = null,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    val colors = QeetrixTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = QeetrixSpace.InsetLg, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        start?.invoke()
        Column(Modifier.weight(1f)) {
            QeetrixText(
                label,
                weight = if (start != null && !danger) QeetrixTextWeight.Semibold else QeetrixTextWeight.Regular,
                tone = if (danger) QeetrixTextTone.Danger else null,
            )
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                QeetrixText(detail, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
            }
        }
        value?.let { QeetrixText(it, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle) }
        end?.invoke()
        if (onClick != null && !danger) {
            if (external) {
                AppIcon(AppIconName.SquareArrowOutUpRight, null, size = QeetrixSize.IconSm, tint = colors.iconSubtle)
            } else {
                QeetrixIcon(
                    QeetrixIconName.ChevronRight,
                    null,
                    size = QeetrixSize.IconSm,
                    tint = colors.iconSubtle,
                    mirrorInRightToLeft = true,
                )
            }
        }
    }
}
