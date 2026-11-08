package com.qeetgroup.qeetid.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.qeetgroup.qeetid.BuildConfig
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.AppIcon
import com.qeetgroup.qeetid.ui.AppIconName
import com.qeetgroup.qeetid.ui.ResultDetails
import com.qeetgroup.qeetid.ui.ResultNote
import com.qeetgroup.qeetid.ui.ResultPage
import com.qeetgroup.qeetid.ui.SealTone
import com.qeetgroup.qeetid.ui.deviceWord
import com.qeetgroup.qeetrix.components.button.QeetrixButton
import com.qeetgroup.qeetrix.components.button.QeetrixButtonSize
import com.qeetgroup.qeetrix.components.button.QeetrixButtonTone
import com.qeetgroup.qeetrix.components.button.QeetrixButtonVariant
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace

/**
 * Update required: Qeet ID no longer accepts this version for sign-in approvals. It says what stops (approvals) and
 * what doesn't (codes), so nobody is locked out of their sites while updating, and that updating keeps the codes and
 * the link. Update opens Google Play at Qeet ID; Show my codes goes back to Accounts.
 */
@Composable
fun UpdateRequiredScreen(onShowCodes: () -> Unit) {
    val context = LocalContext.current
    ResultPage(
        title = stringResource(R.string.update_title),
        body = stringResource(R.string.update_body),
        sealTone = SealTone.Brand,
        seal = { AppIcon(AppIconName.Download, null, Modifier.fillMaxSize()) },
        actions = {
            QeetrixButton(
                stringResource(R.string.update_show_codes),
                onClick = onShowCodes,
                variant = QeetrixButtonVariant.Ghost,
                tone = QeetrixButtonTone.Neutral,
                size = QeetrixButtonSize.Lg,
                fullWidth = true,
            )
            QeetrixButton(
                stringResource(R.string.update_play),
                onClick = { openStore(context) },
                size = QeetrixButtonSize.Lg,
                fullWidth = true,
            )
        },
    ) {
        ResultDetails(listOf(stringResource(R.string.update_this_version) to BuildConfig.VERSION_NAME))
        Spacer(Modifier.height(QeetrixSpace.GapLg))
        ResultNote(AppIconName.KeyRound, stringResource(R.string.update_keeps, deviceWord()))
    }
}

/** Google Play at Qeet ID: the Play Store app, or its web page when there is none. */
private fun openStore(context: Context) {
    val id = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri()))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri()))
        } catch (_: ActivityNotFoundException) {
            // Nothing can open it.
        }
    }
}
