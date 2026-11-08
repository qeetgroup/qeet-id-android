package com.qeetgroup.qeetid.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.GroupLabel
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.NumberedSteps
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.Panel
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetid.ui.ScrollingPage
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetid.ui.initialOf
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetrix.components.alert.QeetrixAlert
import com.qeetgroup.qeetrix.components.alert.QeetrixAlertTone
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.list.QeetrixListItemIcon
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.theme.QeetrixTheme

/**
 * Move to a new phone. Codes are kept only on this phone, not in a backup, which is what keeps them safe; so moving
 * means linking the new phone, adding each account there again, and only then unlinking this one. The accounts on this
 * phone are listed (names only), so nothing is forgotten.
 */
@Composable
fun MoveRoute(navController: NavHostController) {
    val container = LocalAppContainer.current
    val accounts by container.accounts.accounts.collectAsStateWithLifecycle()
    val device = deviceWord()
    val short = isShort()
    val colors = QeetrixTheme.colors
    LaunchedEffect(Unit) { container.accounts.load() }

    ScrollingPage(
        topBar = { ScreenBar(stringResource(R.string.move_title, device), onBack = { navController.popBackStack() }) },
        actions = {
            QeetrixButton(stringResource(R.string.action_done), onClick = {
                navController.popBackStack()
            }, size = QeetrixButtonSize.Lg, fullWidth = true)
        },
    ) {
        PageTitle(stringResource(R.string.move_heading, device), stringResource(R.string.move_body, device))
        Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else 20.dp))
        Panel {
            QeetrixText(
                stringResource(R.string.move_steps),
                size = QeetrixTextSize.Sm,
                weight = QeetrixTextWeight.Semibold,
                tone = QeetrixTextTone.Subtle,
            )
            NumberedSteps(
                listOf(
                    stringResource(R.string.move_step1) to stringResource(R.string.move_step1_note),
                    stringResource(R.string.move_step2) to stringResource(R.string.move_step2_note),
                    stringResource(R.string.move_step3, device) to stringResource(R.string.move_step3_note),
                ),
            )
        }
        Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else 20.dp))
        QeetrixAlert(
            title = stringResource(R.string.move_keep, device),
            description = stringResource(R.string.move_keep_body),
            tone = QeetrixAlertTone.Warning,
        )
        val list = accounts.orEmpty()
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(if (short) 20.dp else QeetrixSpace.LayoutSm))
            Box(Modifier.padding(bottom = QeetrixSpace.GapSm).semantics { heading() }) {
                GroupLabel(stringResource(R.string.move_accounts, device, list.size))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, QeetrixShapes.Surface)
                    .border(1.dp, colors.borderDefault, QeetrixShapes.Surface),
            ) {
                list.forEachIndexed { index, account ->
                    if (index > 0) SettingsRule()
                    val site = account.issuer.ifBlank { account.label }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = QeetrixSpace.InsetMd, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QeetrixListItemIcon(initialOf(site))
                        Column(Modifier.weight(1f)) {
                            QeetrixText(
                                site.ifBlank { stringResource(R.string.account_unnamed) },
                                weight = QeetrixTextWeight.Semibold,
                                maxLines = 1,
                            )
                            if (account.issuer.isNotBlank() && account.label.isNotBlank()) {
                                QeetrixText(account.label, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
