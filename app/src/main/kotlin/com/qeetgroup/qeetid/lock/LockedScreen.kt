package com.qeetgroup.qeetid.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.AppMark
import com.qeetgroup.qeetid.ui.CentredState
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetid.ui.LocalUserVerification
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.foundation.haptics.QeetrixHapticRole
import com.qeetgroup.qeetrix.foundation.haptics.rememberQeetrixHaptics
import com.qeetgroup.qeetrix.foundation.sizing.QeetrixDensity
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.icons.QeetrixIconName
import com.qeetgroup.qeetrix.theme.LocalQeetrixDensity
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

/**
 * Screen 21: the app is locked. It asks for fingerprint, face or screen lock as soon as it shows, and again on Unlock.
 * Nothing behind it is composed while it shows, so no code is computed or read out.
 */
@Composable
fun LockedScreen() {
    val container = LocalAppContainer.current
    val verification = LocalUserVerification.current
    val scope = rememberCoroutineScope()
    val haptics = rememberQeetrixHaptics()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    suspend fun unlock() {
        busy = true
        failed = false
        val result = verification.verify(VerifyPrompt(title, subtitle))
        busy = false
        when (result) {
            VerifyResult.Verified, VerifyResult.Unavailable -> container.lock.unlock()
            VerifyResult.Failed -> {
                failed = true
                haptics.perform(QeetrixHapticRole.Error)
            }
            VerifyResult.Cancelled -> Unit
        }
    }

    LaunchedEffect(Unit) { unlock() }

    CompositionLocalProvider(LocalQeetrixDensity provides QeetrixDensity.Comfortable) {
        CentredState(
            Modifier
                .fillMaxSize()
                .background(QeetrixTheme.colors.surfaceCanvas)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AppMark()
            Spacer(Modifier.height(QeetrixSpace.InsetLg))
            QeetrixHeading(
                stringResource(R.string.locked_title),
                level = 1,
                size = QeetrixHeadingSize.TitleMd,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QeetrixSpace.GapSm))
            QeetrixText(stringResource(R.string.locked_body), tone = QeetrixTextTone.Subtle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(QeetrixSpace.LayoutSm))
            QeetrixButton(
                stringResource(R.string.locked_unlock),
                onClick = { scope.launch { unlock() } },
                icon = QeetrixIconName.Lock,
                loading = busy,
            )
            if (failed) {
                Spacer(Modifier.height(QeetrixSpace.GapMd))
                QeetrixText(
                    stringResource(R.string.locked_failed),
                    size = QeetrixTextSize.Sm,
                    tone = QeetrixTextTone.Danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
