package com.qeetgroup.qeetid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBar
import com.qeetgroup.qeetrix.components.actionbar.QeetrixBottomActionBarArrangement
import com.qeetgroup.qeetrix.components.navigation.QeetrixBackButton
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar
import com.qeetgroup.qeetrix.components.typography.QeetrixHeading
import com.qeetgroup.qeetrix.components.typography.QeetrixHeadingSize
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

/** The widest a tablet's reading column gets: a phone's width, centred, as on the mockups. */
val ReadingWidth = 480.dp

/** A tablet: the smaller side at least 600 dp. */
@Composable
fun isTablet(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

/** The window's height, in dp: the mockups size their phones by it (640, 700, 800). */
@Composable
fun windowHeightDp(): Int {
    val height = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { height.toDp().value.toInt() }
}

/** A short phone (640 to 700 dp tall): tighter spacing and smaller heroes, so the page fits without scrolling. */
@Composable
fun isShort(): Boolean = !isTablet() && windowHeightDp() < 700

/** "phone" or "tablet", for copy that names the device. */
@Composable
fun deviceWord(): String = stringResource(if (isTablet()) R.string.device_tablet else R.string.device_phone)

/** Keeps content to [ReadingWidth], centred, on tablets; full width on phones. */
fun Modifier.readingColumn(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadingWidth)

/** A screen's ruled bar: back, the title and, under it, a line (a step, a count). */
@Composable
fun ScreenBar(
    title: String,
    onBack: (() -> Unit)?,
    description: String? = null,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    QeetrixTopAppBar(
        title = title,
        description = description,
        navigationIcon = onBack?.let { back -> { QeetrixBackButton(back) } },
        actions = actions,
    )
}

/**
 * A page that scrolls between [topBar] and [actions] (a [QeetrixBottomActionBar]'s buttons), its content in the
 * reading column with the phone's gutter.
 */
@Composable
fun ScrollingPage(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val short = isShort()
    Screen(
        modifier = modifier,
        topBar = topBar,
        // Two actions as the mockups draw Android's bar: the quiet one at its own width at the start, the primary filling
        // the rest.
        bottomBar = {
            if (actions != null) {
                QeetrixBottomActionBar(scroll = scroll, arrangement = QeetrixBottomActionBarArrangement.PrimaryFills, content = actions)
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Column(
                Modifier
                    .readingColumn()
                    .padding(
                        start = QeetrixSpace.InsetLg,
                        end = QeetrixSpace.InsetLg,
                        top = if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm,
                        bottom = if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm,
                    ),
                content = content,
            )
        }
    }
}

/** A heading and its line, the way the mockups open a page. */
@Composable
fun PageTitle(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    centred: Boolean = false,
) {
    val short = isShort()
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        QeetrixHeading(
            title,
            level = 1,
            size = if (short) QeetrixHeadingSize.HeadingSm else QeetrixHeadingSize.HeadingMd,
            textAlign = if (centred) TextAlign.Center else TextAlign.Unspecified,
        )
        if (body != null) {
            Spacer(Modifier.height(if (short) QeetrixSpace.GapSm else QeetrixSpace.GapMd))
            QeetrixText(
                body,
                size = if (short) QeetrixTextSize.Md else QeetrixTextSize.Lg,
                tone = QeetrixTextTone.Subtle,
                textAlign = if (centred) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }
}

/** A raised, ruled 4 dp panel (Qeetrix's card look) for facts, steps and details. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = QeetrixTheme.colors
    val short = isShort()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, QeetrixShapes.Surface)
            .border(1.dp, colors.borderDefault, QeetrixShapes.Surface)
            .padding(if (short) QeetrixSpace.InsetMd else QeetrixSpace.InsetLg),
        verticalArrangement = Arrangement.spacedBy(if (short) QeetrixSpace.GapSm else QeetrixSpace.GapMd),
        content = content,
    )
}

/** One promise, with a green tick on a fixed column; [mark] replaces the tick (a red cross for what stops). */
@Composable
fun Fact(
    text: String,
    mark: QeetrixIconName = QeetrixIconName.Check,
    danger: Boolean = false,
) {
    val colors = QeetrixTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
        QeetrixIcon(
            mark,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp),
            size = QeetrixSize.IconSm,
            tint = if (danger) colors.iconDanger else colors.iconSuccess,
        )
        QeetrixText(text, size = if (isShort()) QeetrixTextSize.Sm else QeetrixTextSize.Md)
    }
}

/** A cross for something that stops, beside [Fact]'s ticks. */
@Composable
fun StopFact(text: String) {
    val colors = QeetrixTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
        QeetrixIcon(
            QeetrixIconName.X,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp),
            size = QeetrixSize.IconSm,
            tint = colors.iconDanger,
        )
        QeetrixText(text, size = if (isShort()) QeetrixTextSize.Sm else QeetrixTextSize.Md)
    }
}

/** A small label above a group (Qeetrix's group labels are the only uppercase). */
@Composable
fun GroupLabel(text: String) {
    QeetrixText(text.uppercase(), size = QeetrixTextSize.Sm, weight = QeetrixTextWeight.Semibold, tone = QeetrixTextTone.Subtle)
}

/** Numbered steps on a fixed column: a brand number tile, the step and a line under it. */
@Composable
fun NumberedSteps(steps: List<Pair<String, String>>) {
    val colors = QeetrixTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(if (isShort()) QeetrixSpace.GapSm else QeetrixSpace.GapMd)) {
        steps.forEachIndexed { index, (title, note) ->
            Row(horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapMd)) {
                Box(
                    Modifier.size(24.dp).background(colors.bgBrandSubtle, QeetrixShapes.Sm).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    QeetrixText(
                        (index + 1).toString(),
                        size = QeetrixTextSize.Sm,
                        weight = QeetrixTextWeight.Semibold,
                        tone = QeetrixTextTone.Brand,
                    )
                }
                Column {
                    QeetrixText(title, weight = QeetrixTextWeight.Semibold)
                    QeetrixText(note, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
                }
            }
        }
    }
}

/**
 * How something ended (Phone linked, Approved, Denied, Request ended, Update required): the icon with a seal on the
 * soft brand wash, edge to edge under the status bar, the outcome and a line, then whatever follows ([content]: a
 * ruled card of facts, a note), and the actions.
 */
@Composable
fun ResultPage(
    title: String,
    body: String,
    sealTone: SealTone,
    seal: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val short = isShort()
    val tablet = isTablet()
    val scroll = rememberScrollState()
    Screen(bottomBar = {
        QeetrixBottomActionBar(scroll = scroll, arrangement = QeetrixBottomActionBarArrangement.PrimaryFills, content = actions)
    }) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .brandWash()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .readingColumn()
                    .heightIn(min = 0.dp)
                    .padding(horizontal = QeetrixSpace.InsetXl, vertical = QeetrixSpace.InsetXl),
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
                    sealTone = sealTone,
                    seal = seal,
                )
                Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                PageTitle(title, body, centred = true)
                Spacer(Modifier.height(if (short) QeetrixSpace.InsetLg else QeetrixSpace.LayoutSm))
                content()
            }
        }
    }
}

/** A result's facts, label and value on each ruled row (Browser · Chrome on macOS), in a raised 4 dp card. */
@Composable
fun ResultDetails(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val colors = QeetrixTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, QeetrixShapes.Surface)
            .border(1.dp, colors.borderDefault, QeetrixShapes.Surface),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = QeetrixSpace.InsetLg, vertical = QeetrixSpace.InsetMd),
                horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QeetrixText(label, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
                QeetrixText(
                    value,
                    modifier = Modifier.weight(1f),
                    weight = QeetrixTextWeight.Semibold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

/** What happens next, or what to do, with an icon at its start: brand, or amber when it's a warning. */
@Composable
fun ResultNote(
    icon: AppIconName,
    text: String,
    warning: Boolean = false,
) {
    val colors = QeetrixTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
        AppIcon(
            icon,
            null,
            Modifier.padding(top = 2.dp),
            size = QeetrixSize.IconSm,
            tint = if (warning) colors.iconWarning else colors.iconBrand,
        )
        QeetrixText(text, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
    }
}

/** [ResultNote] with one of Qeetrix's own icons. */
@Composable
fun ResultNote(
    icon: QeetrixIconName,
    text: String,
    warning: Boolean = false,
) {
    val colors = QeetrixTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QeetrixSpace.GapSm)) {
        QeetrixIcon(
            icon,
            null,
            Modifier.padding(top = 2.dp),
            size = QeetrixSize.IconSm,
            tint = if (warning) colors.iconWarning else colors.iconBrand,
        )
        QeetrixText(text, size = QeetrixTextSize.Sm, tone = QeetrixTextTone.Subtle)
    }
}
