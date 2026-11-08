package com.qeetgroup.qeetid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetrix.theme.LocalQeetrixContentColor
import com.qeetgroup.qeetrix.theme.QeetrixTheme
import kotlin.math.roundToInt

/**
 * Qeet ID's icon inside the app: the white keyhole on the Qeet orange gradient, round as Android masks the home
 * screen icon, on every screen (the rounded square is iOS's). Logo colours, not UI colours, so the same in light and
 * dark.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    contentDescription: String? = stringResource(R.string.app_mark_description),
) {
    Image(
        painterResource(R.drawable.qeet_id_mark_round),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/** The icon on brand rings, by how much room the screen has. */
enum class RingsSize(
    val box: Dp,
    val inner: Dp,
    val mark: Dp,
) {
    Lg(208.dp, 28.dp, 104.dp),
    Md(160.dp, 20.dp, 80.dp),
    Sm(112.dp, 14.dp, 56.dp),
    Xs(80.dp, 10.dp, 44.dp),
}

/** The seal on the icon's corner: the brand's ring, a green tick for something done, red for something stopped. */
enum class SealTone { Brand, Success, Danger }

/**
 * The hero of Qeet ID's welcoming and result screens: the icon on two soft brand rings, with an optional [seal]
 * (an icon, drawn in the seal's content colour) on its lower corner. The seal is 0.6 of the icon, its centre a tenth of
 * the icon in from the corner, at every size, so it never hides the keyhole.
 */
@Composable
fun MarkRings(
    size: RingsSize,
    modifier: Modifier = Modifier,
    sealTone: SealTone = SealTone.Brand,
    seal: (@Composable () -> Unit)? = null,
) {
    val colors = QeetrixTheme.colors
    Box(modifier.size(size.box).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.box).background(colors.bgBrandSubtle.copy(alpha = 0.6f), CircleShape))
        Box(Modifier.padding(size.inner).size(size.box - size.inner * 2).background(colors.bgBrandSubtle, CircleShape))
        BrandMark(size = size.mark, contentDescription = null)
        if (seal != null) {
            val sealSize = (size.mark.value * 0.6f).roundToInt().dp
            val (background, ring, content) =
                when (sealTone) {
                    SealTone.Brand -> Triple(colors.surfaceRaised, colors.borderBrand, colors.iconBrand)
                    SealTone.Success -> Triple(colors.bgSuccessBold, colors.surfaceRaised, colors.iconOnBold)
                    SealTone.Danger -> Triple(colors.bgDangerBold, colors.surfaceRaised, colors.iconOnBold)
                }
            Box(
                Modifier
                    .offset(x = size.mark * 0.4f, y = size.mark * 0.4f)
                    .size(sealSize)
                    .shadow(4.dp, CircleShape)
                    .background(background, CircleShape)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalQeetrixContentColor provides content) {
                    Box(Modifier.size(sealSize * 0.52f), contentAlignment = Alignment.Center) { seal() }
                }
            }
        }
    }
}

/** The soft brand wash behind a hero, edge to edge under the status bar, fading into the page. */
@Composable
fun Modifier.brandWash(): Modifier {
    val colors = QeetrixTheme.colors
    return background(Brush.verticalGradient(listOf(colors.bgBrandSubtle, colors.surfaceCanvas, colors.surfaceCanvas)))
}

/** The soft brand panel a picture sits on (a notification preview, a computer and a phone). */
@Composable
fun Modifier.brandPanel(): Modifier {
    val colors = QeetrixTheme.colors
    return background(
        Brush.verticalGradient(listOf(colors.bgBrandSubtle, colors.bgBrandSubtle.copy(alpha = 0.4f))),
        com.qeetgroup.qeetrix.foundation.shape.QeetrixShapes.Surface,
    )
}

/** A colour a picture of something else's screen uses (a website, a laptop), not Qeet ID's own UI. */
internal object Picture {
    val Page = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1917)
    val Muted = Color(0xFF57534E)
    val Bezel = Color(0xFF2A2C31)
    val Backdrop = Color(0xFF1B1D21)
}
