package com.qeetgroup.qeetid.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.BuildConfig
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.BrandMark
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetid.ui.ScreenBar
import com.qeetgroup.qeetrix.components.drawer.QeetrixBottomSheet
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarHost
import com.qeetgroup.qeetrix.components.toast.QeetrixSnackbarTone
import com.qeetgroup.qeetrix.components.toast.rememberQeetrixSnackbarHostState
import com.qeetgroup.qeetrix.components.typography.QeetrixText
import com.qeetgroup.qeetrix.components.typography.QeetrixTextSize
import com.qeetgroup.qeetrix.components.typography.QeetrixTextTone
import com.qeetgroup.qeetrix.components.typography.QeetrixTextWeight
import com.qeetgroup.qeetrix.foundation.spacing.QeetrixSpace
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlinx.coroutines.launch

private const val HELP_CENTRE = "https://docs.qeet.in/id"
private const val SUPPORT_EMAIL = "support@qeet.in"
private const val PRIVACY = "https://id.qeet.in/legal/privacy"
private const val TERMS = "https://id.qeet.in/legal/terms"
private const val APACHE_2 = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL = "https://openfontlicense.org"

/** What Qeet ID is built with, and under which licence: names, not translated. */
private val THIRD_PARTY =
    listOf(
        Triple("AndroidX and Jetpack Compose", "Apache License 2.0", APACHE_2),
        Triple("CameraX", "Apache License 2.0", APACHE_2),
        Triple("Firebase Cloud Messaging", "Apache License 2.0", APACHE_2),
        Triple("Kotlin, kotlinx.coroutines and kotlinx.serialization", "Apache License 2.0", APACHE_2),
        Triple("OkHttp", "Apache License 2.0", APACHE_2),
        Triple("ZXing", "Apache License 2.0", APACHE_2),
        Triple("Fira Code", "SIL Open Font License 1.1", OFL),
    )

/**
 * About and help: the version (tap to copy it with the build number, to quote when asking for help), help first, then
 * the legal pages. An arrow out marks a row that leaves the app.
 */
@Composable
fun AboutRoute(navController: NavHostController) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbars = rememberQeetrixSnackbarHostState()
    var licences by remember { mutableStateOf(false) }
    val version = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    val copied = stringResource(R.string.about_version_copied)

    Box(Modifier.fillMaxSize()) {
        Screen(topBar = { ScreenBar(stringResource(R.string.about_title), onBack = { navController.popBackStack() }) }) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(bottom = QeetrixSpace.LayoutSm),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(
                        start = QeetrixSpace.InsetXl,
                        end = QeetrixSpace.InsetXl,
                        top = 28.dp,
                        bottom = QeetrixSpace.InsetXl,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandMark(size = 64.dp, contentDescription = null)
                    Spacer(Modifier.height(QeetrixSpace.GapMd))
                    QeetrixText(stringResource(R.string.app_name), size = QeetrixTextSize.Lg, weight = QeetrixTextWeight.Semibold)
                    QeetrixText(
                        version,
                        modifier =
                            Modifier
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.about_copy_version)) {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(version, version)))
                                        snackbars.show(copied, QeetrixSnackbarTone.Success)
                                    }
                                }.padding(QeetrixSpace.GapXs),
                        size = QeetrixTextSize.Sm,
                        tone = QeetrixTextTone.Subtle,
                    )
                }

                SettingsGroup(stringResource(R.string.about_help)) {
                    SettingsRow(
                        label = stringResource(R.string.about_help_centre),
                        detail = stringResource(R.string.about_help_centre_body),
                        external = true,
                        onClick = { open(context, HELP_CENTRE) },
                    )
                    SettingsRule()
                    SettingsRow(
                        label = stringResource(R.string.about_contact),
                        detail = SUPPORT_EMAIL,
                        external = true,
                        onClick = { open(context, "mailto:$SUPPORT_EMAIL", Intent.ACTION_SENDTO) },
                    )
                }

                SettingsGroup(stringResource(R.string.about_legal)) {
                    SettingsRow(label = stringResource(R.string.about_privacy), external = true, onClick = { open(context, PRIVACY) })
                    SettingsRule()
                    SettingsRow(label = stringResource(R.string.about_terms), external = true, onClick = { open(context, TERMS) })
                    SettingsRule()
                    SettingsRow(label = stringResource(R.string.about_licences), onClick = { licences = true })
                }

                val colors = QeetrixTheme.colors
                QeetrixText(
                    buildAnnotatedString {
                        append(stringResource(R.string.about_from))
                        append(" ")
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textDefault),
                        ) { append(stringResource(R.string.about_group)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = QeetrixTextSize.Sm,
                    tone = QeetrixTextTone.Subtle,
                    textAlign = TextAlign.Center,
                )
                QeetrixText(
                    stringResource(R.string.about_copyright),
                    modifier = Modifier.fillMaxWidth(),
                    size = QeetrixTextSize.Sm,
                    tone = QeetrixTextTone.Subtle,
                    textAlign = TextAlign.Center,
                )
            }
        }
        QeetrixSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    QeetrixBottomSheet(
        open = licences,
        onOpenChange = { licences = it },
        title = stringResource(R.string.about_licences),
        description = stringResource(R.string.about_licences_body),
    ) {
        THIRD_PARTY.forEachIndexed { index, (name, licence, url) ->
            if (index > 0) SettingsRule()
            SettingsRow(label = name, detail = licence, external = true, onClick = { open(context, url) })
        }
    }
}

/** Opens [uri] outside the app; nothing when nothing can open it. */
private fun open(
    context: Context,
    uri: String,
    action: String = Intent.ACTION_VIEW,
) {
    try {
        context.startActivity(Intent(action, uri.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser or mail app.
    }
}
