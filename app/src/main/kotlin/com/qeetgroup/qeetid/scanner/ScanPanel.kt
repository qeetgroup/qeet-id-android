package com.qeetgroup.qeetid.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.QeetrixTheme

/**
 * A result over the camera, on the overlay surface with Qeetrix's 4 dp corners: what was found and what next, its
 * buttons sharing the width with their labels centred.
 */
@Composable
fun ScanPanel(
    title: String,
    body: String,
    icon: QeetrixIconName? = null,
    warning: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = QeetrixTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, QeetrixShapes.Surface)
            .background(colors.surfaceOverlay, QeetrixShapes.Surface)
            .padding(QeetrixSpace.InsetMd),
        verticalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd), verticalAlignment = Alignment.Top) {
            when {
                leading != null -> {
                    leading()
                }

                icon != null -> {
                    QeetrixIcon(
                        icon,
                        null,
                        Modifier.padding(top = 2.dp),
                        tint = if (warning) colors.iconWarning else colors.iconInfo,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                QeetrixText(title, weight = QeetrixTextWeight.Semibold)
                QeetrixText(body, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm), content = actions)
    }
}
