package com.qeetgroup.qeetid.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetid.ui.MarkRings
import com.qeetgroup.qeetid.ui.PageTitle
import com.qeetgroup.qeetid.ui.RingsSize
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.SignInRequestRoute
import com.qeetgroup.qeetid.ui.brandWash
import com.qeetgroup.qeetid.ui.isShort
import com.qeetgroup.qeetid.ui.isTablet
import com.qeetgroup.qeetid.ui.readingColumn
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.haptics.QeetrixHapticRole
import com.qeetgroup.qeetrix.foundation.haptics.rememberQeetrixHaptics
import com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixDensity
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIcon
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.LocalQeetrixDensity
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

/**
 * Locked: the icon with a lock seal on the brand wash, "Qeet ID is locked", and a full-width Unlock. It asks for the
 * fingerprint or screen lock as soon as it shows, and again on Unlock. Opened from a sign-in request's notification,
 * it says one is waiting. Nothing behind it is composed while it shows, so no code is computed or read out.
 */
@Composable
fun LockedScreen() {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val scope = rememberCoroutineScope()
    val haptics = rememberQeetrixHaptics()
    val next by container.inbox.next.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val tablet = isTablet()
    val short = isShort()

    suspend fun unlock() {
        busy = true
        failed = false
        val result = verification.verify(VerifyPrompt(title, subtitle))
        busy = false
        when (result) {
            VerifyResult.Verified, VerifyResult.Unavailable -> {
                container.lock.unlock()
            }

            VerifyResult.Failed -> {
                failed = true
                haptics.perform(QeetrixHapticRole.Error)
            }

            VerifyResult.Cancelled -> {}
        }
    }

    LaunchedEffect(Unit) { unlock() }

    CompositionLocalProvider(LocalQeetrixDensity provides QeetrixDensity.Comfortable) {
        Screen(
            bottomBar = {
                QeetrixBottomActionBar {
                    QeetrixButton(
                        stringResource(R.string.locked_unlock),
                        onClick = { scope.launch { unlock() } },
                        modifier = Modifier.readingColumn(),
                        size = QeetrixButtonSize.Lg,
                        icon = QeetrixIconName.FingerprintPattern,
                        loading = busy,
                        fullWidth = true,
                    )
                }
            },
        ) {
            // The brand wash, edge to edge under the status bar, behind the hero.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .brandWash()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.readingColumn().padding(QeetrixSpace.InsetXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MarkRings(
                        if (tablet) {
                            RingsSize.Lg
                        } else if (short) {
                            RingsSize.Sm
                        } else {
                            RingsSize.Md
                        },
                        seal = { QeetrixIcon(QeetrixIconName.Lock, null, Modifier.fillMaxSize()) },
                    )
                    Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                    PageTitle(stringResource(R.string.locked_title), stringResource(R.string.locked_body), centred = true)
                    if (failed) {
                        Spacer(Modifier.height(QeetrixSpace.GapMd))
                        QeetrixText(
                            stringResource(R.string.locked_failed),
                            size = QeetrixTextSize.Sm,
                            tone = QeetrixTextTone.Danger,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (next is SignInRequestRoute) {
                        Spacer(Modifier.height(QeetrixSpace.LayoutSm))
                        RequestWaiting()
                    }
                }
            }
        }
    }
}

/** The card a sign-in request's notification opens the locked app with. */
@Composable
private fun RequestWaiting() {
    val colors = QeetrixTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, QeetrixShapes.Surface)
            .border(1.dp, colors.borderDefault, QeetrixShapes.Surface)
            .padding(QeetrixSpace.InsetMd),
        horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.bgBrandSubtle, CircleShape), contentAlignment = Alignment.Center) {
            QeetrixIcon(QeetrixIconName.BellRing, null, tint = colors.iconBrand)
        }
        Column {
            QeetrixText(stringResource(R.string.locked_request_waiting), weight = QeetrixTextWeight.Semibold)
            QeetrixText(stringResource(R.string.locked_request_waiting_body), size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
        }
    }
}
